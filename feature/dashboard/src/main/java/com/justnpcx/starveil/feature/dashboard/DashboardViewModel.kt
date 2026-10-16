package com.justnpcx.starveil.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.KillSwitchController
import com.justnpcx.starveil.core.kernel.TrafficPolicy
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.kernel.config.ConfigBuildException
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.ui.components.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One rate sample, kept for the traffic sparkline. */
data class TrafficSample(val downlink: Long, val uplink: Long)

/** Single source of truth for the Dashboard screen. */
data class DashboardUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val stats: TrafficStats = TrafficStats(),
    /** Bounded ring of recent rates; never grows past [HISTORY_SIZE]. */
    val history: List<TrafficSample> = emptyList(),
    /** The selected node or group's name, or null when nothing is selected. */
    val selectedNodeName: String? = null,
    val isBusy: Boolean = false,
    /** What to tell the user, as data. See [DashboardNotice]. */
    val notice: DashboardNotice? = null,
    /**
     * The last self-check found traffic escaping. A warning, not a failure:
     * the tunnel is up and carrying traffic, it just is not trustworthy.
     */
    val leakDetected: Boolean = false,
) {
    /**
     * What the core draws.
     *
     * Kept apart from [connectionState] on purpose: that one is the tunnel's
     * own state machine, which connect/disconnect write, and "a leak was
     * detected" is a fact about its health rather than a fifth state the
     * machine can be in. Folding it in here keeps the mapping in one place
     * instead of in every reader.
     */
    val coreState: ConnectionState
        get() = if (connectionState == ConnectionState.CONNECTED && leakDetected) {
            ConnectionState.WARNING
        } else {
            connectionState
        }

    companion object {
        const val HISTORY_SIZE = 48
    }
}

/**
 * What the dashboard is telling the user, as data rather than as a sentence.
 *
 * The view model has no Context, and copy written into it is copy the resource
 * system never sees: untranslatable, and invisible to anyone reviewing the
 * app's wording (C1). [KernelRefused] carries the one part that is not fixed
 * text — the kernel's own reason, which the bridge has already redacted.
 */
sealed interface DashboardNotice {
    /** The Kill Switch is blocking every connection, on purpose. */
    data object KillSwitchBlocking : DashboardNotice

    /** The tunnel died and auto-reconnect is off. */
    data object TunnelDropped : DashboardNotice

    /** The tunnel died and is being brought back. */
    data object TunnelReconnecting : DashboardNotice

    /** The reconnect died too; we stop rather than loop. */
    data object TunnelKeepsDropping : DashboardNotice

    /** Nothing is selected to connect to. */
    data object NoTarget : DashboardNotice

    /** The selected target cannot be turned into a config. */
    data object ConfigUnbuildable : DashboardNotice

    /** The kernel refused to start. [reason] is already redacted. */
    data class KernelRefused(val reason: String?) : DashboardNotice
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val kernel: KernelBridge,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
    private val groups: GroupRepository,
    private val rules: RuleRepository,
    private val configBuilder: ConfigBuilder,
) : ViewModel() {

    // Structured concurrency: one SupervisorJob per ViewModel, cancelled in
    // onCleared(). No GlobalScope anywhere in this project.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statsJob: Job? = null

    /**
     * One automatic reconnect per user-initiated connection.
     *
     * A reconnect that itself dies must not be retried, or a node that crashes
     * the kernel every time would be retried forever. The user tapping connect
     * again is what grants the next attempt — deliberately not the kernel
     * reporting [KernelState.Running], since a kernel that starts and
     * immediately dies would re-arm itself on every cycle.
     */
    private var canAutoReconnect = true

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // The kernel pushes its lifecycle, so an unexpected death arrives here
        // without polling. Only recoverable errors are retryable (Crashed /
        // StartFailed / Unknown); Stopped is the user stopping, which must
        // never be "recovered".
        scope.launch {
            kernel.state.collect { state ->
                if (state is KernelState.Error && state.error.recoverable) onKernelDropped()
            }
        }
        // Keep the label in sync with the choice made on the Nodes tab — and
        // apply it. The kernel runs the config that was built at connect time
        // (RealKernelBridge.start is a no-op while running), so without this
        // the dashboard would name a node the kernel is not using, and every
        // connection would silently fail against the old one.
        scope.launch {
            // The node and the group are one choice, not two: the repository
            // keeps at most one set. combine can still emit twice for a single
            // user action, because both keys change in the same edit, so the
            // target is compared by identity and duplicates are dropped.
            var lastKey: String? = null
            preferences.connectionTarget.collect { target ->
                _uiState.update { it.copy(selectedNodeName = targetLabel(target)) }

                val key = target.key()
                if (key == lastKey) return@collect
                // The first value is the flow replaying what is already stored;
                // that is not a switch and must not restart anything.
                val isReplay = lastKey == null
                lastKey = key
                if (isReplay) return@collect

                if (target is ConnectionTarget.None) {
                    // The target was deleted, emptied, or turned back into a
                    // plain folder. There is nothing to switch to, so stop
                    // rather than build a config from something that is gone.
                    if (_uiState.value.connectionState == ConnectionState.CONNECTED) disconnect()
                    return@collect
                }
                if (!_uiState.value.isBusy && _uiState.value.connectionState == ConnectionState.CONNECTED) {
                    applyTargetSwitch()
                }
            }
        }
        // A leak the self-check found is what turns a healthy-looking tunnel
        // into a warning. Observed rather than polled, like everything else.
        scope.launch {
            preferences.lastLeakCheck.collect { record ->
                _uiState.update { it.copy(leakDetected = record.leaking) }
            }
        }
    }

    /** Point the running tunnel at the newly selected target. */
    private fun applyTargetSwitch() {
        scope.launch {
            disconnect()
            connect(userInitiated = true)
        }
    }

    /**
     * The kernel died under us. If the user had a tunnel up and asked for
     * auto-reconnect, bring it back; otherwise settle into BLOCKED and say
     * what happened rather than leaving a stale CONNECTED label.
     */
    private suspend fun onKernelDropped() {
        // Only a drop from a believed-connected state is ours to recover from.
        // A crash while disconnected (or while blocked) is not a lost tunnel.
        if (_uiState.value.connectionState != ConnectionState.CONNECTED) return
        if (_uiState.value.isBusy) return

        if (!preferences.autoReconnect.first()) {
            _uiState.update {
                it.copy(connectionState = ConnectionState.BLOCKED, notice = DashboardNotice.TunnelDropped)
            }
            return
        }
        if (!canAutoReconnect) {
            _uiState.update {
                it.copy(connectionState = ConnectionState.BLOCKED, notice = DashboardNotice.TunnelKeepsDropping)
            }
            return
        }

        canAutoReconnect = false
        connect(notice = DashboardNotice.TunnelReconnecting)
    }

    /** Tap on the core: connect or disconnect. */
    fun onCoreClick() {
        if (_uiState.value.isBusy) return
        // Reads the tunnel's own state, not [DashboardUiState.coreState]: a
        // warning means the tunnel *is* up, so a tap must still take it down.
        //
        // The machine never writes WARNING here — it is derived in coreState —
        // but it is grouped with CONNECTED rather than ignored, so the tap
        // cannot start meaning the opposite if that ever changes.
        when (_uiState.value.connectionState) {
            ConnectionState.DISCONNECTED, ConnectionState.BLOCKED ->
                scope.launch { connect(userInitiated = true) }

            ConnectionState.CONNECTED, ConnectionState.WARNING -> scope.launch { disconnect() }
            ConnectionState.CONNECTING -> { /* ignore taps while connecting */ }
        }
    }

    /**
     * @param userInitiated true when the user (or a node switch) asked for
     *   this, which re-arms the single automatic reconnect.
     * @param notice what to tell the user while this attempt runs. A plain
     *   connect clears whatever was there; the automatic reconnect passes the
     *   reason it is retrying, because that is the whole message — setting it
     *   beforehand and then calling this would wipe it on the next line.
     */
    private suspend fun connect(
        userInitiated: Boolean = false,
        notice: DashboardNotice? = null,
    ) {
        if (userInitiated) canAutoReconnect = true
        _uiState.update {
            it.copy(isBusy = true, connectionState = ConnectionState.CONNECTING, notice = notice)
        }

        // Kill Switch, panic mode. Asked as "would traffic be allowed if we
        // were up?", so the decision comes from the one policy function rather
        // than a second reading of the mode here.
        val alwaysBlocked = KillSwitchController.policyFor(
            preferences.killSwitchMode.first(),
            connected = true,
        ) == TrafficPolicy.BLOCK
        if (alwaysBlocked) {
            startBlocked()
            return
        }

        // The real outbound: whatever is selected on the Nodes tab — either a
        // single node, or a group the kernel picks within.
        val target = resolveTarget()
        val rules = rules.observeAllOrdered().first()
        val config = try {
            when (target) {
                is Target.Node -> configBuilder.build(target.profile, rules)
                is Target.Group -> configBuilder.build(target.members, rules, target.mode)
                Target.None -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            connectionState = ConnectionState.DISCONNECTED,
                            notice = DashboardNotice.NoTarget,
                        )
                    }
                    return
                }
            }
        } catch (e: ConfigBuildException) {
            _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.BLOCKED,
                    notice = DashboardNotice.ConfigUnbuildable,
                )
            }
            return
        }

        val result = kernel.start(config)
        if (result.isSuccess) {
            _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.CONNECTED,
                    selectedNodeName = target.label(),
                    // A retry that succeeded must not leave "reconnecting…" on
                    // screen. This is only reachable now that [connect] lets a
                    // caller's notice survive: it used to be cleared on the
                    // next line, which also hid this.
                    notice = null,
                )
            }
            startStatsLoop()
        } else {
            // The bridge reports a redacted reason (PRD §8.3: tell the
            // truth on failure, never a bare "failed to start").
            val reason = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.BLOCKED,
                    notice = DashboardNotice.KernelRefused(reason),
                )
            }
        }
    }

    private suspend fun disconnect() {
        _uiState.update { it.copy(isBusy = true) }
        statsJob?.cancel()

        // "Block on disconnect" means the tunnel stays up with nothing
        // forwarding it. Tearing it down here is exactly the leak this mode
        // exists to prevent: whatever was in flight would go out directly.
        //
        // Asked as "once we are down, would traffic be allowed?" — that is the
        // question the policy answers, and it covers ALWAYS_BLOCK too.
        val keepTunnel = KillSwitchController.policyFor(
            preferences.killSwitchMode.first(),
            connected = false,
        ) == TrafficPolicy.BLOCK
        if (keepTunnel) kernel.stopKeepingTunnel() else kernel.stop()

        _uiState.update {
            it.copy(
                isBusy = false,
                connectionState = if (keepTunnel) {
                    ConnectionState.BLOCKED
                } else {
                    ConnectionState.DISCONNECTED
                },
                stats = TrafficStats(),
                history = emptyList(),
                notice = if (keepTunnel) DashboardNotice.KillSwitchBlocking else null,
            )
        }
    }

    /** Bring the tunnel up with nothing but a reject, and say why. */
    private suspend fun startBlocked() {
        val result = kernel.start(configBuilder.buildBlocking())
        _uiState.update {
            it.copy(
                isBusy = false,
                connectionState = ConnectionState.BLOCKED,
                stats = TrafficStats(),
                history = emptyList(),
                notice = if (result.isSuccess) {
                    DashboardNotice.KillSwitchBlocking
                } else {
                    DashboardNotice.KernelRefused(
                        result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() },
                    )
                },
            )
        }
    }

    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(1000)
                val stats = kernel.queryStats()
                _uiState.update { state ->
                    state.copy(
                        stats = stats,
                        history = (state.history + TrafficSample(stats.downlinkRate, stats.uplinkRate))
                            .takeLast(DashboardUiState.HISTORY_SIZE),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        statsJob?.cancel()
        scope.cancel()
        super.onCleared()
    }

    /**
     * What the tunnel should point at.
     *
     * A group carries its name and members rather than the [ProxyGroup] itself,
     * so the connect path cannot accidentally re-read a group that changed
     * underneath it.
     */
    private sealed interface Target {
        data object None : Target

        data class Node(val profile: Profile) : Target

        data class Group(val name: String, val members: List<Profile>, val mode: GroupMode) : Target

        /** Null for [None]: there is no name to show, and no copy belongs here. */
        fun label(): String? = when (this) {
            is Node -> profile.name
            is Group -> name
            None -> null
        }
    }

    /**
     * Turns the stored selection into something buildable.
     *
     * A group only counts as a target when its mode produces a group outbound
     * and it has at least one member left: a plain folder is not something you
     * connect *through*, and an empty group has nothing to build from. Both
     * degrade to [Target.None] rather than failing later.
     */
    private suspend fun resolveTarget(): Target {
        return when (val selected = preferences.connectionTarget.first()) {
            is ConnectionTarget.Group -> {
                val group: ProxyGroup = groups.getById(selected.id) ?: return Target.None
                if (group.mode == GroupMode.MANUAL) return Target.None
                val members = group.profileIds.mapNotNull { profiles.getById(it) }
                if (members.isEmpty()) return Target.None
                Target.Group(group.name, members, group.mode)
            }

            is ConnectionTarget.Node ->
                profiles.getById(selected.id)?.let { Target.Node(it) } ?: Target.None

            ConnectionTarget.None -> Target.None
        }
    }

    /** Identity of the current target, so a change can be told from a repeat. */
    private fun ConnectionTarget.key(): String = when (this) {
        is ConnectionTarget.Node -> "node:$id"
        is ConnectionTarget.Group -> "group:$id"
        ConnectionTarget.None -> NO_TARGET
    }

    private suspend fun targetLabel(target: ConnectionTarget): String? = when (target) {
        is ConnectionTarget.Node -> profiles.getById(target.id)?.name
        is ConnectionTarget.Group -> groups.getById(target.id)?.name
        ConnectionTarget.None -> null
    }

    private companion object {
        /** No target key: nothing is selected. Not copy — a map key. */
        const val NO_TARGET = "none"
    }
}
