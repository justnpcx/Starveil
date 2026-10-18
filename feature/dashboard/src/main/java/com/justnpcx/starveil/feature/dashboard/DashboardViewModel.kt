package com.justnpcx.starveil.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.scene.SceneApplyState
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.KillSwitchController
import com.justnpcx.starveil.core.kernel.TrafficPolicy
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.model.NodeCandidate
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.NodePicker
import com.justnpcx.starveil.core.model.NodeRecommendation
import com.justnpcx.starveil.core.model.NodeRegions
import com.justnpcx.starveil.core.model.RecommendationReason
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.displayName
import com.justnpcx.starveil.core.model.nodeHealthOf
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
import kotlinx.coroutines.flow.combine
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
    /**
     * The selected node's health over the sampling window (§7.2), or null when
     * the target is a group — a group has no single curve. A selected node with
     * no samples yet is present but [NodeHealth.collecting].
     */
    val health: NodeHealth? = null,
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

    /** Auto-select picked a node; [reason] is what the picker decided on. */
    data class AutoSelected(val nodeName: String, val reason: RecommendationReason) : DashboardNotice

    /** The tunnel died and a different node is being tried instead. */
    data object SwitchingNode : DashboardNotice
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val kernel: KernelBridge,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
    private val groups: GroupRepository,
    private val connection: ConnectionController,
    private val nodeHealth: NodeHealthRepository,
    private val sceneApply: SceneApplyState,
) : ViewModel() {

    // Structured concurrency: one SupervisorJob per ViewModel, cancelled in
    // onCleared(). No GlobalScope anywhere in this project.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statsJob: Job? = null

    /**
     * The last target the collector acted on.
     *
     * A field rather than a local so [switchToNextBest] can pre-empt it: a
     * switch this view model made on purpose must not come back around as a
     * "the user changed the target" event and restart the tunnel a second time.
     */
    private var lastTargetKey: String? = null

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
                when (state) {
                    is KernelState.Error ->
                        if (state.error.recoverable) onKernelDropped()
                    // Started or stopped without this view model asking: the
                    // quick-settings tile, or the notification's Disconnect.
                    // Reconcile rather than assume — see the two handlers.
                    KernelState.Running -> onRunningElsewhere()
                    KernelState.Stopped -> onStoppedElsewhere()
                    else -> Unit
                }
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
            preferences.connectionTarget.collect { target ->
                _uiState.update { it.copy(selectedNodeName = targetLabel(target)) }

                val key = target.key()
                if (key == lastTargetKey) return@collect
                // The first value is the flow replaying what is already stored;
                // that is not a switch and must not restart anything.
                val isReplay = lastTargetKey == null
                lastTargetKey = key
                if (isReplay) return@collect

                if (target is ConnectionTarget.None) {
                    // The target was deleted, emptied, or turned back into a
                    // plain folder. There is nothing to switch to, so stop
                    // rather than build a config from something that is gone.
                    if (_uiState.value.connectionState == ConnectionState.CONNECTED) disconnect()
                    return@collect
                }
                if (!_uiState.value.isBusy && _uiState.value.connectionState == ConnectionState.CONNECTED) {
                    applyConfigChange()
                }
            }
        }
        // The routing policy is part of the config for the same reason the node
        // is: the kernel runs what it built at connect time. Without this, the
        // rules page would say "in force" about a policy the tunnel has not
        // been told, which is exactly what happened on device.
        scope.launch {
            var lastPolicy: Pair<RoutePreset, Boolean>? = null
            combine(preferences.routePreset, preferences.blockAds) { preset, ads -> preset to ads }
                .collect { policy ->
                    val previous = lastPolicy
                    lastPolicy = policy
                    // The first emission is the stored value, not a change.
                    if (previous == null || previous == policy) return@collect
                    if (_uiState.value.isBusy) return@collect
                    // A scene that just rebuilt the tunnel already wrote this
                    // preset; rebuilding again would tear it down twice for one
                    // network switch.
                    if (System.currentTimeMillis() - sceneApply.lastAppliedAtMs < SCENE_DEDUPE_MS) {
                        return@collect
                    }
                    if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
                        applyConfigChange()
                    }
                }
        }
        // The selected node's health (§7.2). Combined with the target rather
        // than re-subscribed per switch: one query returns every node's
        // samples, so picking a different one out of it costs nothing.
        scope.launch {
            combine(
                preferences.connectionTarget,
                nodeHealth.observeAll(),
                profiles.observeAll(),
            ) { target, byNode, nodes ->
                // A group target has no curve of its own — the chart is about a
                // node. A node with no samples is present-but-collecting, which
                // the chart says in words rather than leaving blank.
                val id = (target as? ConnectionTarget.Node)?.id ?: return@combine null
                // A *stale* id is a different thing: a subscription refresh
                // replaces a group's nodes with new ids, so the selection can
                // point at a node that no longer exists while its samples are
                // still in the window. Drawing those would describe something
                // that is gone. Seen on device.
                if (nodes.none { it.id == id }) return@combine null
                nodeHealthOf(byNode[id].orEmpty())
            }.collect { health ->
                _uiState.update { it.copy(health = health) }
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

    /** Rebuild the running tunnel against the stored config. */
    private fun applyConfigChange() {
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

        // Switching before reconnecting, when asked to: retrying the node that
        // just died is the least useful thing to do with a drop.
        val switched = preferences.autoSwitchOnDrop.first() && switchToNextBest()
        connect(notice = if (switched) DashboardNotice.SwitchingNode else DashboardNotice.TunnelReconnecting)
    }

    /**
     * Pick the best node by the picker's rules and select it.
     *
     * Selecting is the whole action: if the tunnel is up, the target-switch
     * path already reconnects to whatever is selected; if it is down, this
     * arms the next connect. Doing the connect here as well would race that
     * path.
     */
    fun autoSelect() {
        if (_uiState.value.isBusy) return
        scope.launch {
            val pick = bestNode() ?: run {
                _uiState.update { it.copy(notice = DashboardNotice.NoTarget) }
                return@launch
            }
            val name = profiles.getById(pick.profileId)?.name.orEmpty()
            _uiState.update { it.copy(notice = DashboardNotice.AutoSelected(name, pick.reason)) }
            // The target collector does the rest: it reconnects when the tunnel
            // is up, and only arms the selection when it is not. Doing the
            // connect here as well would race that path.
            preferences.setSelectedProfileId(pick.profileId)
        }
    }

    /**
     * Point the selection at the best node that is not [excluding].
     *
     * Only for a single-node target. A group already has urltest choosing
     * within it, and switching away from the group would throw that away —
     * the very thing it is good at.
     */
    private suspend fun switchToNextBest(): Boolean {
        val current = (preferences.connectionTarget.first() as? ConnectionTarget.Node)?.id
            ?: return false
        val pick = bestNode(exclude = current) ?: return false
        if (pick.profileId == current) return false
        // Claim the change before it is delivered: otherwise the collector
        // sees a target it did not act on and starts a second reconnect.
        lastTargetKey = ConnectionTarget.Node(pick.profileId).key()
        preferences.setSelectedProfileId(pick.profileId)
        return true
    }

    /**
     * The picker's answer over the current node list, or null when there is
     * nothing to choose from.
     *
     * The preferred region is the region of the node the user already chose:
     * that is the only manual preference the app stores, and inventing a
     * second one would be a setting nobody asked for.
     */
    private suspend fun bestNode(exclude: Long? = null): NodeRecommendation? {
        val nodes = profiles.observeAll().first()
        if (nodes.isEmpty()) return null
        val samples = nodeHealth.observeAll().first()
        val selectedId = preferences.selectedProfileId.first()
        val preferredRegion = nodes.firstOrNull { it.id == selectedId }
            ?.let { NodeRegions.classify(it.name)?.region }

        val candidates = nodes
            .filter { it.id != exclude }
            .map { profile ->
                NodeCandidate(
                    profileId = profile.id,
                    region = NodeRegions.classify(profile.name)?.region,
                    health = samples[profile.id]?.let(::nodeHealthOf),
                )
            }
        return NodePicker.recommend(
            candidates = candidates,
            preferredRegion = preferredRegion,
            nowMs = System.currentTimeMillis(),
        )
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

        // The whole decision — kill switch, target, rules, preset files,
        // per-app exceptions — belongs to ConnectionController, because the
        // quick-settings tile needs exactly the same answer. This maps the
        // outcome onto what the screen says.
        when (val outcome = connection.connect()) {
            is ConnectionOutcome.Connected -> {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        connectionState = ConnectionState.CONNECTED,
                        selectedNodeName = outcome.label,
                        // A retry that succeeded must not leave "reconnecting…"
                        // on screen. This is only reachable now that [connect]
                        // lets a caller's notice survive: it used to be cleared
                        // on the next line, which also hid this.
                        notice = null,
                    )
                }
                startStatsLoop()
            }

            is ConnectionOutcome.Blocked -> _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.BLOCKED,
                    stats = TrafficStats(),
                    history = emptyList(),
                    notice = if (outcome.started) {
                        DashboardNotice.KillSwitchBlocking
                    } else {
                        DashboardNotice.KernelRefused(outcome.reason)
                    },
                )
            }

            ConnectionOutcome.NoTarget -> _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.DISCONNECTED,
                    notice = DashboardNotice.NoTarget,
                )
            }

            ConnectionOutcome.ConfigUnbuildable -> _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.BLOCKED,
                    notice = DashboardNotice.ConfigUnbuildable,
                )
            }

            is ConnectionOutcome.Refused -> _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.BLOCKED,
                    notice = DashboardNotice.KernelRefused(outcome.reason),
                )
            }

            // Only disconnect() produces this; listed so the mapping stays
            // exhaustive when a variant is added.
            ConnectionOutcome.Disconnected -> Unit
        }
    }

    private suspend fun disconnect() {
        _uiState.update { it.copy(isBusy = true) }
        statsJob?.cancel()

        // Same controller the tile uses. The Kill Switch decides whether the
        // tunnel stays up, so the two entry points cannot disagree about it.
        val blocked = connection.disconnect() is ConnectionOutcome.Blocked
        _uiState.update {
            it.copy(
                isBusy = false,
                connectionState = if (blocked) {
                    ConnectionState.BLOCKED
                } else {
                    ConnectionState.DISCONNECTED
                },
                stats = TrafficStats(),
                history = emptyList(),
                notice = if (blocked) DashboardNotice.KillSwitchBlocking else null,
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
     * The kernel came up without this view model asking — the tile, most
     * likely. Without this the screen would sit on DISCONNECTED over a working
     * tunnel, and the next tap would try to connect again.
     *
     * A running kernel is not always a connected one: under "always block" the
     * tunnel is up with nothing forwarding it, so the state is BLOCKED. The
     * policy is the only thing that can tell the two apart from here.
     *
     * Ignored while this view model is mid-connect — it is about to set the
     * same state from the outcome it already has.
     */
    private suspend fun onRunningElsewhere() {
        if (_uiState.value.isBusy) return
        if (_uiState.value.connectionState == ConnectionState.CONNECTED) return
        // A blocking kernel is running too, so the policy alone would call it
        // "connected" — the bridge's own flag is what tells them apart.
        val blocked = kernel.blocking.value || KillSwitchController.policyFor(
            preferences.killSwitchMode.first(),
            connected = true,
        ) == TrafficPolicy.BLOCK
        _uiState.update {
            it.copy(
                isBusy = false,
                connectionState = if (blocked) {
                    ConnectionState.BLOCKED
                } else {
                    ConnectionState.CONNECTED
                },
                selectedNodeName = targetLabel(preferences.connectionTarget.first()),
                notice = if (blocked) DashboardNotice.KillSwitchBlocking else null,
            )
        }
        if (!blocked) startStatsLoop()
    }

    /**
     * The kernel stopped, and it may not have been this view model that did it.
     *
     * Asked the same way [disconnect] asks: "once we are down, would traffic be
     * allowed?" — so a Kill Switch user who disconnects from the notification
     * ends up blocked rather than exposed, and the answer does not depend on
     * who pressed the button.
     *
     * Idempotent on purpose. When this view model stopped the kernel itself,
     * the same emission arrives after it has already set the same state, and
     * recomputing it from the same policy is a no-op rather than a race.
     */
    private suspend fun onStoppedElsewhere() {
        val blocking = KillSwitchController.policyFor(
            preferences.killSwitchMode.first(),
            connected = false,
        ) == TrafficPolicy.BLOCK
        statsJob?.cancel()
        _uiState.update {
            it.copy(
                connectionState = if (blocking) {
                    ConnectionState.BLOCKED
                } else {
                    ConnectionState.DISCONNECTED
                },
                stats = TrafficStats(),
                history = emptyList(),
            )
        }
    }

    /** Identity of the current target, so a change can be told from a repeat. */
    private fun ConnectionTarget.key(): String = when (this) {
        is ConnectionTarget.Node -> "node:$id"
        is ConnectionTarget.Group -> "group:$id"
        ConnectionTarget.None -> NO_TARGET
    }

    private suspend fun targetLabel(target: ConnectionTarget): String? = when (target) {
        // displayName, not name: the same node is drawn with its flag on the
        // Nodes tab and in the connect outcome, and a preference write that
        // makes this collector run again must not be the moment the flag
        // disappears. It did, on device.
        is ConnectionTarget.Node -> profiles.getById(target.id)?.displayName()
        is ConnectionTarget.Group -> groups.getById(target.id)?.name
        ConnectionTarget.None -> null
    }

    private companion object {
        /** How long a scene's own rebuild suppresses this one. */
        const val SCENE_DEDUPE_MS = 3_000L

        /** No target key: nothing is selected. Not copy — a map key. */
        const val NO_TARGET = "none"
    }
}
