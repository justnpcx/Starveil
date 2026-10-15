package com.justnpcx.starveil.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.kernel.config.ConfigBuildException
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.model.KillSwitchMode
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
    val selectedNodeName: String = NO_NODE,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
) {
    companion object {
        const val NO_NODE = "No node selected"
        const val HISTORY_SIZE = 48
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val kernel: KernelBridge,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
    private val rules: RuleRepository,
    private val configBuilder: ConfigBuilder,
) : ViewModel() {

    // Structured concurrency: one SupervisorJob per ViewModel, cancelled in
    // onCleared(). No GlobalScope anywhere in this project.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statsJob: Job? = null

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Keep the label in sync with the choice made on the Nodes tab — and
        // apply it. The kernel runs the config that was built at connect time
        // (RealKernelBridge.start is a no-op while running), so without this
        // the dashboard would name a node the kernel is not using, and every
        // connection would silently fail against the old one.
        scope.launch {
            var replayedInitialValue = false
            preferences.selectedProfileId.collect { id ->
                val name = if (id == 0L) DashboardUiState.NO_NODE else profiles.getById(id)?.name
                _uiState.update { it.copy(selectedNodeName = name ?: DashboardUiState.NO_NODE) }
                // A flow replays its current value on subscribe; that is not a
                // switch and must not restart anything.
                if (!replayedInitialValue) {
                    replayedInitialValue = true
                    return@collect
                }
                if (id == 0L) {
                    // The selected node was deleted on the Nodes tab. There is
                    // nothing to switch to, so stop — rather than try to build
                    // a config from a profile that no longer exists.
                    if (_uiState.value.connectionState == ConnectionState.CONNECTED) disconnect()
                    return@collect
                }
                if (!_uiState.value.isBusy && _uiState.value.connectionState == ConnectionState.CONNECTED) {
                    applyNodeSwitch()
                }
            }
        }
    }

    /** Point the running tunnel at the newly selected node. */
    private fun applyNodeSwitch() {
        scope.launch {
            disconnect()
            connect()
        }
    }

    /** Tap on the core: connect or disconnect. */
    fun onCoreClick() {
        if (_uiState.value.isBusy) return
        when (_uiState.value.connectionState) {
            ConnectionState.DISCONNECTED, ConnectionState.BLOCKED -> scope.launch { connect() }
            ConnectionState.CONNECTED -> scope.launch { disconnect() }
            ConnectionState.CONNECTING -> { /* ignore taps while connecting */ }
        }
    }

    private suspend fun connect() {
        _uiState.update {
            it.copy(isBusy = true, connectionState = ConnectionState.CONNECTING, errorMessage = null)
        }

        // Kill Switch, panic mode: bring the tunnel up with nothing but a
        // reject. No node is read, so no credential is touched and nothing can
        // reach the network — the user has to turn the mode off to get out.
        if (preferences.killSwitchMode.first() == KillSwitchMode.ALWAYS_BLOCK) {
            startBlocked()
            return
        }

        // The real outbound: whatever node is selected on the Nodes tab.
        val id = preferences.selectedProfileId.first()
        val profile = if (id == 0L) null else profiles.getById(id)
        if (profile == null) {
            _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.DISCONNECTED,
                    errorMessage = "Pick a node on the Nodes tab first",
                )
            }
            return
        }

        val config = try {
            configBuilder.build(profile, rules.observeAllOrdered().first())
        } catch (e: ConfigBuildException) {
            _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.BLOCKED,
                    errorMessage = "Cannot build the config for this node",
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
                    selectedNodeName = profile.name,
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
                    errorMessage = reason ?: "Kernel failed to start",
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
        val keepTunnel = preferences.killSwitchMode.first() == KillSwitchMode.BLOCK_ON_DISCONNECT
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
                errorMessage = if (keepTunnel) KILL_SWITCH_BLOCKING else null,
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
                errorMessage = if (result.isSuccess) {
                    KILL_SWITCH_BLOCKING
                } else {
                    result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                        ?: "Kernel failed to start"
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

    private companion object {
        /** Why the dashboard is in BLOCKED: the user has to turn it off. */
        const val KILL_SWITCH_BLOCKING = "Kill Switch is blocking all traffic"
    }
}
