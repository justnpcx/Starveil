package com.justnpcx.starveil.feature.dashboard

import androidx.lifecycle.ViewModel
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.ui.components.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Single source of truth for the Dashboard screen. */
data class DashboardUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val stats: TrafficStats = TrafficStats(),
    /** Placeholder: real node selector replaces this in Phase 3. */
    val selectedNodeName: String = "No node selected",
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val kernel: KernelBridge,
) : ViewModel() {

    // Structured concurrency: one SupervisorJob per ViewModel, cancelled in
    // onCleared(). No GlobalScope anywhere in this project.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statsJob: Job? = null

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

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
            _uiState.update { it.copy(isBusy = true, connectionState = ConnectionState.CONNECTING, errorMessage = null) }
            // Skeleton config: real config comes from ConfigBuilder in Phase 2.
            val result = kernel.start(config = """{"log":{"level":"info"}}""")
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        connectionState = ConnectionState.CONNECTED,
                        selectedNodeName = "Demo Node 1",
                    )
                }
                startStatsLoop()
            } else {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        connectionState = ConnectionState.BLOCKED,
                        errorMessage = "Kernel failed to start (skeleton: FakeKernelBridge)",
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
                    selectedNodeName = "No node selected",
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
