package com.justnpcx.starveil.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.kernel.config.ConfigBuildException
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
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

/** Single source of truth for the Dashboard screen. */
data class DashboardUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val stats: TrafficStats = TrafficStats(),
    val selectedNodeName: String = NO_NODE,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
) {
    companion object {
        const val NO_NODE = "No node selected"
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val kernel: KernelBridge,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
    private val configBuilder: ConfigBuilder,
) : ViewModel() {

    // Structured concurrency: one SupervisorJob per ViewModel, cancelled in
    // onCleared(). No GlobalScope anywhere in this project.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statsJob: Job? = null

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Keep the label in sync with the choice made on the Nodes tab.
        scope.launch {
            preferences.selectedProfileId.collect { id ->
                val name = if (id == 0L) DashboardUiState.NO_NODE else profiles.getById(id)?.name
                _uiState.update { it.copy(selectedNodeName = name ?: DashboardUiState.NO_NODE) }
            }
        }
    }

    /** Tap on the core: connect or disconnect. */
    fun onCoreClick() {
        if (_uiState.value.isBusy) return
        when (_uiState.value.connectionState) {
            ConnectionState.DISCONNECTED, ConnectionState.BLOCKED -> connect()
            ConnectionState.CONNECTED -> disconnect()
            ConnectionState.CONNECTING -> { /* ignore taps while connecting */ }
        }
    }

    private fun connect() {
        scope.launch {
            _uiState.update {
                it.copy(isBusy = true, connectionState = ConnectionState.CONNECTING, errorMessage = null)
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
                return@launch
            }

            val config = try {
                configBuilder.build(profile)
            } catch (e: ConfigBuildException) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        connectionState = ConnectionState.BLOCKED,
                        errorMessage = "Cannot build the config for this node",
                    )
                }
                return@launch
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
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        connectionState = ConnectionState.BLOCKED,
                        errorMessage = "Kernel failed to start",
                    )
                }
            }
        }
    }

    private fun disconnect() {
        scope.launch {
            _uiState.update { it.copy(isBusy = true) }
            statsJob?.cancel()
            kernel.stop()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    connectionState = ConnectionState.DISCONNECTED,
                    stats = TrafficStats(),
                )
            }
        }
    }

    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(1000)
                val stats = kernel.queryStats()
                _uiState.update { it.copy(stats = stats) }
            }
        }
    }

    override fun onCleared() {
        statsJob?.cancel()
        scope.cancel()
        super.onCleared()
    }
}
