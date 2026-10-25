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
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.STICKINESS_MARGIN
import com.justnpcx.starveil.core.model.pickerPolicySummary
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
     * The node the last auto-pick moved to, or null.
     *
     * The card reads it to say whether the current node was chosen for the user
     * or by them — a mode, not a button. It is not persisted: it describes this
     * session's pick, and after a restart the honest answer is "manual".
     */
    val autoPickedName: String? = null,
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
    /**
     * The tunnel came up but nothing has gone through it.
     *
     * The kernel's state cannot say this: "running" means the config was
     * accepted, not that a packet reached the node. A wrong credential, a dead
     * node or a route into nowhere all look identical from the state machine,
     * and all of them leave the phone with no internet while the icon says
     * connected. See [TrafficWatch].
     */
    val tunnelSilent: Boolean = false,
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
        get() = if (
            connectionState == ConnectionState.CONNECTED && (leakDetected || tunnelSilent)
        ) {
            ConnectionState.WARNING
        } else {
            connectionState
        }

    companion object {
        const val HISTORY_SIZE = 30
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

    /**
     * The block was asked for but the kernel would not start. Said out loud:
     * the alternative is a screen that claims to be blocking while traffic
     * goes out directly.
     */
    data object KillSwitchFailed : DashboardNotice

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

    /** The tunnel died and a different node is being tried instead. */
    data object SwitchingNode : DashboardNotice

    /**
     * The tunnel is up and nothing has gone through it.
     *
     * A warning rather than a failure: the tunnel exists, and something may yet
     * arrive. What it must not do is stay silent — a connected-looking app with
     * no internet is the state users cannot diagnose on their own.
     */
    data object TunnelSilent : DashboardNotice
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val kernel: KernelBridge,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
    private val groups: GroupRepository,
    private val connection: ConnectionController,
    private val nodeHealth: NodeHealthRepository,
    private val recovery: DropRecovery,
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

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * Whether anything has gone through the tunnel since this connect.
     *
     * Armed by [connect], settled by the first proxied connection the kernel
     * reports, and closed by [trafficWatchJob] after the window. Reset on
     * disconnect: a silent tunnel is a fact about *this* connection.
     */
    private val trafficWatch = TrafficWatch()
    private var trafficWatchJob: Job? = null

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
            var lastPolicy: Policy? = null
            combine(
                preferences.routePreset,
                preferences.blockAds,
                preferences.subscribedRulePacks,
                preferences.localApiEnabled,
            ) { preset, ads, packs, api -> Policy(preset, ads, packs, api) }
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
        // The one signal the kernel's state cannot give: a packet actually
        // left through the node. Armed by connect(), settled here.
        scope.launch {
            kernel.connections.collect { connection ->
                if (!connection.isProxied()) return@collect
                if (trafficWatch.onProxied(System.currentTimeMillis()) ==
                    TrafficWatch.Verdict.Carrying
                ) {
                    trafficWatchJob?.cancel()
                    _uiState.update { it.copy(tunnelSilent = false) }
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

    /**
     * Everything a rebuild depends on, compared as one value so one user action
     * (which may write two keys) rebuilds once.
     */
    private data class Policy(
        val preset: RoutePreset,
        val blockAds: Boolean,
        val packs: Set<String>,
        val localApi: Boolean,
    )

    /**
     * Rebuild the running tunnel against the stored config.
     *
     * The rebuild itself lives in [ConnectionController.restart]; what stays
     * here is the one thing the controller cannot know — that this is the
     * user's policy change rather than a retry, so the one automatic
     * reconnect is re-armed. `a rebuild re-arms the one-shot auto-reconnect`
     * is the test that keeps that true.
     */
    private fun applyConfigChange() {
        scope.launch {
            recovery.rearm()
            connection.restart()
        }
    }

    /**
     * The kernel died under us. The policy lives in [DropRecovery]; this maps
     * its decision onto the screen and, when it says so, moves the selection
     * before reconnecting.
     */
    private suspend fun onKernelDropped() {
        when (
            val decision = recovery.decide(
                connected = _uiState.value.connectionState == ConnectionState.CONNECTED,
                busy = _uiState.value.isBusy,
            )
        ) {
            DropRecovery.Decision.Ignore -> Unit

            is DropRecovery.Decision.GiveUp -> _uiState.update {
                it.copy(connectionState = ConnectionState.BLOCKED, notice = decision.notice)
            }

            is DropRecovery.Decision.Reconnect -> {
                val pick = decision.switchedTo
                if (pick != null) {
                    // Claim the change before it is delivered: otherwise the
                    // target collector sees a target it did not act on and
                    // starts a second reconnect.
                    lastTargetKey = ConnectionTarget.Node(pick.profileId).key()
                    preferences.setSelectedProfileId(pick.profileId)
                }
                connect(
                    notice = if (pick != null) {
                        DashboardNotice.SwitchingNode
                    } else {
                        DashboardNotice.TunnelReconnecting
                    },
                )
            }
        }
    }

    /**
     * Pick the best node by the picker's rules and select it.
     *
     * Selecting is the whole action: if the tunnel is up, the target-switch
     * path already reconnects to whatever is selected; if it is down, this
     * arms the next connect. Doing the connect here as well would race that
     * path.
     */
    /**
     * Pick the best node now.
     *
     * Silent when the pick is the node already selected: a button that reports
     * "nothing changed" every time it is pressed is noise, and the card already
     * shows which node is in force. A failure is the one case worth a word,
     * because then nothing happened and the user cannot see why.
     */
    fun autoSelect() {
        if (_uiState.value.isBusy) return
        scope.launch {
            val pick = recovery.autoPick() ?: run {
                _uiState.update { it.copy(notice = DashboardNotice.NoTarget) }
                return@launch
            }
            val target = pick.recommendation
            val winner = profiles.getById(target.profileId)
            val current = pick.currentId?.let { profiles.getById(it) }

            // Every press says what it saw, even when it does nothing. The
            // complaint this answers was "it only ever shows a toast and never
            // switches"; the fix is not a better toast, it is the numbers
            // behind the decision being on the record.
            KernelDiagnostics.log(
                "auto-select: probes=TCP ${winner?.server}:${winner?.port}; " +
                    "${pickerPolicySummary()}; " +
                    if (pick.switch) {
                        "switched ${current?.displayName() ?: "none"} → " +
                            "${winner?.displayName()}, delta ${pick.delta} pts " +
                            "(${pick.currentScore} → ${target.score})"
                    } else {
                        "kept ${current?.displayName() ?: winner?.displayName()}: " +
                            "best challenger ${winner?.displayName()} scores " +
                            "${target.score}, delta ${pick.delta} < " +
                            "${STICKINESS_MARGIN}"
                    },
            )

            // A pick inside the margin is not a switch: the card stays put and
            // no notice appears, which is the behaviour the button is supposed
            // to have when nothing has actually changed.
            if (!pick.switch) return@launch

            // The target collector does the rest: it reconnects when the tunnel
            // is up, and only arms the selection when it is not. Doing the
            // connect here as well would race that path.
            preferences.setSelectedProfileId(target.profileId)
            // The result is the card itself — new name, new latency. This flag
            // only turns the badge from "manual" to "auto".
            _uiState.update { it.copy(autoPickedName = winner?.displayName().orEmpty()) }
        }
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
        if (userInitiated) recovery.rearm()
        _uiState.update {
            it.copy(
                isBusy = true,
                connectionState = ConnectionState.CONNECTING,
                notice = notice,
                // A new attempt is a new question; the last answer does not
                // carry over.
                tunnelSilent = false,
            )
        }
        trafficWatchJob?.cancel()
        trafficWatch.reset()

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
                // The tunnel exists; whether it carries anything is a separate
                // question, and the one the user finds out about the hard way.
                startTrafficWatch()
            }

            is ConnectionOutcome.Blocked -> {
                // `started` is the difference between a tunnel that drops
                // packets and no tunnel at all. BLOCKED is a claim about the
                // traffic, so only make it when the kernel that enforces it is
                // running — the same rule disconnect() follows since 9930507.
                val blocked = outcome.started
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
                        notice = if (blocked) {
                            DashboardNotice.KillSwitchBlocking
                        } else {
                            DashboardNotice.KillSwitchFailed
                        },
                    )
                }
            }

            ConnectionOutcome.NoTarget -> _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.DISCONNECTED,
                    notice = DashboardNotice.NoTarget,
                )
            }

            // No tunnel was started, so traffic still leaves directly. Saying
            // BLOCKED here would be the same false claim the Kill Switch fix
            // removed; the notice is what carries the failure.
            ConnectionOutcome.ConfigUnbuildable -> _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.DISCONNECTED,
                    notice = DashboardNotice.ConfigUnbuildable,
                )
            }

            is ConnectionOutcome.Refused -> _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.DISCONNECTED,
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
        trafficWatchJob?.cancel()
        trafficWatch.reset()

        // Same controller the tile uses. The Kill Switch decides whether the
        // tunnel stays up, so the two entry points cannot disagree about it.
        val outcome = connection.disconnect()
        // `Blocked` means the policy asked for a block; `started` says whether
        // the blocking config actually came up. A failed start leaves no
        // tunnel at all, so reporting BLOCKED would be a lie — and a leak the
        // user would not look for.
        val blocked = outcome is ConnectionOutcome.Blocked && outcome.started
        val blockFailed = outcome is ConnectionOutcome.Blocked && !outcome.started
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
                notice = when {
                    blocked -> DashboardNotice.KillSwitchBlocking
                    blockFailed -> DashboardNotice.KillSwitchFailed
                    else -> null
                },
            )
        }
    }

    /**
     * Start the countdown that decides whether this tunnel carries anything.
     *
     * Polled rather than scheduled: the deadline is ten seconds away, the app
     * is in the foreground with a screen on, and a job that cancels itself is
     * simpler to reason about than a timer that has to be cancelled from three
     * other places.
     */
    private fun startTrafficWatch() {
        trafficWatchJob?.cancel()
        trafficWatch.arm(System.currentTimeMillis())
        trafficWatchJob = scope.launch {
            while (isActive && trafficWatch.isArmed) {
                delay(TRAFFIC_WATCH_TICK_MS)
                if (trafficWatch.check(System.currentTimeMillis()) == TrafficWatch.Verdict.Silent) {
                    _uiState.update {
                        it.copy(tunnelSilent = true, notice = DashboardNotice.TunnelSilent)
                    }
                    // Logged as well as shown: the Logs screen is what a bug
                    // report gets, and "connected, nothing through" is the
                    // line that explains a report saying "it says connected
                    // but nothing loads".
                    KernelDiagnostics.log(
                        "tunnel up but nothing proxied within ${TrafficWatch.WINDOW_MS} ms",
                    )
                    return@launch
                }
            }
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
        trafficWatchJob?.cancel()
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
        // The bridge's own flag, not the policy: a kernel that is running and
        // forwarding is a connection, and only a blackhole sets `blocking`.
        // Reading the policy here would call a working tunnel "blocked" the
        // moment the switch says always-block.
        val blocked = kernel.blocking.value
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
        // A stopped kernel is not a block. The blackhole keeps the tunnel and
        // sets `blocking`; a stop without it leaves traffic free. Reading the
        // policy instead is how a fresh launch under "always block" claimed
        // 阻断中 with no tunnel, no kernel, and nothing being dropped.
        val blocking = kernel.blocking.value
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

/**
 * Whether the kernel sent this connection through the proxy.
 *
 * `direct` and `block` are not the tunnel doing its job: one is traffic that
 * never entered it, the other is traffic it refused. Everything else — a
 * protocol name — is the node carrying it.
 */
private fun KernelConnection.isProxied(): Boolean =
    outboundType.isNotBlank() && outboundType != "direct" && outboundType != "block"

/** How often the traffic watch re-checks its deadline. */
private const val TRAFFIC_WATCH_TICK_MS = 1_000L
