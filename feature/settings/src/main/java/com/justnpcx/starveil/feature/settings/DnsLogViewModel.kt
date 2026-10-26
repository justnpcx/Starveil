package com.justnpcx.starveil.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.geo.DnsEntry
import com.justnpcx.starveil.core.data.geo.DnsMetrics
import com.justnpcx.starveil.core.data.geo.DnsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DnsLogUiState(
    val entries: List<DnsEntry> = emptyList(),
    val snapshot: DnsSnapshot = DnsSnapshot.EMPTY,
    /** Exactly what the copy button would put on the clipboard. */
    val exportText: String = "",
)

/**
 * The per-query DNS log.
 *
 * It reads the same ring the aggregate row reads, so the two can never
 * disagree about how many samples there are. A slow tick keeps an open screen
 * current without making every lookup on the DNS path wake the UI.
 */
@HiltViewModel
class DnsLogViewModel @Inject constructor(
    private val metrics: DnsMetrics,
) : ViewModel() {

    private val _state = MutableStateFlow(DnsLogUiState())
    val state: StateFlow<DnsLogUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(TICK_MS)
            }
        }
    }

    fun refresh() {
        _state.value = DnsLogUiState(
            entries = metrics.recentEntries(),
            snapshot = metrics.snapshot(),
            exportText = metrics.exportText(),
        )
    }

    fun clear() {
        metrics.clear()
        refresh()
    }

    private companion object {
        const val TICK_MS = 2_000L
    }
}
