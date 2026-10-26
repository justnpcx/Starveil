package com.justnpcx.starveil.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.kernel.ConnectionLog
import com.justnpcx.starveil.core.model.ConnectionDetail
import com.justnpcx.starveil.core.model.displayName
import com.justnpcx.starveil.core.model.nodeHealthOf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The three filters the table offers, and whether history is shown. */
data class ConnectionsFilters(
    /** True = only rows that are still open. The default. */
    val onlyActive: Boolean = true,
    /** Package name, or null for every app. */
    val app: String? = null,
    /** Outbound tag, or null for every node. */
    val node: String? = null,
    /** The kernel's rule text, or null for every rule. */
    val rule: String? = null,
) {
    val any: Boolean get() = app != null || node != null || rule != null
}

data class ConnectionsUiState(
    /** Already filtered, in the kernel log's order (open first). */
    val rows: List<ConnectionDetail> = emptyList(),
    /** Everything the ring holds, for the empty-state wording. */
    val total: Int = 0,
    val filters: ConnectionsFilters = ConnectionsFilters(),
    /** Distinct values in the ring, for the filter menus. */
    val apps: List<String> = emptyList(),
    val nodes: List<String> = emptyList(),
    val rules: List<String> = emptyList(),
    /** A clock, so an open row's duration ticks without the kernel reporting. */
    val nowMs: Long = 0L,
    /** Exit node latency, keyed by outbound tag. See the screen's note. */
    val latencyByTag: Map<String, Long> = emptyMap(),
    /** Human name for a tag, when one can be resolved with certainty. */
    val nodeNameByTag: Map<String, String> = emptyMap(),
)

/**
 * The live connection table.
 *
 * Everything here is folded from the kernel's own events — nothing is inferred
 * from the configured rules. The one number that is not the kernel's is the
 * latency column: the connection record carries no round-trip time, so the
 * screen shows the exit node's measured latency when the outbound tag can be
 * resolved with certainty, and says so in a note under the table.
 */
@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    log: ConnectionLog,
    profiles: ProfileRepository,
    health: NodeHealthRepository,
    preferences: PreferencesRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(ConnectionsFilters())
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val latencyByTag = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val nodeNameByTag = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        // One tick a second, only while the screen is in front of someone: the
        // duration of an open row is the only thing that needs a clock, and a
        // kernel that reports no update for an idle keep-alive would otherwise
        // leave it frozen.
        viewModelScope.launch {
            while (isActive) {
                now.value = System.currentTimeMillis()
                delay(TICK_MS)
            }
        }
        // "proxy" is the tag a single-node config gives its node, so it can be
        // resolved with certainty; "proxy-<n>" is an index into whatever list
        // the config was built from, which the app does not keep. Rather than
        // guess, those rows show a dash.
        viewModelScope.launch {
            combine(
                profiles.observeAll(),
                health.observeAll(),
                preferences.selectedProfileId,
            ) { all, healthByNode, selectedId ->
                val selected = all.firstOrNull { it.id == selectedId }
                val ema = selected?.let { healthByNode[it.id] }?.let { nodeHealthOf(it).emaMs }
                if (selected != null) {
                    mapOf("proxy" to selected.displayName()) to
                        (if (ema != null) mapOf("proxy" to ema) else emptyMap())
                } else {
                    emptyMap<String, String>() to emptyMap<String, Long>()
                }
            }.collect { (names, latency) ->
                nodeNameByTag.value = names
                latencyByTag.value = latency
            }
        }
    }

    val uiState: StateFlow<ConnectionsUiState> =
        combine(
            log.connections,
            filters,
            now,
            combine(latencyByTag, nodeNameByTag) { latency, names -> latency to names },
        ) { rows, currentFilters, clock, latencyAndNames ->
            ConnectionsUiState(
                rows = filterRows(rows, currentFilters),
                total = rows.size,
                filters = currentFilters,
                apps = rows.map { it.appPackage }.filter { it.isNotBlank() }.distinct().sorted(),
                nodes = rows.map { it.outbound }.filter { it.isNotBlank() }.distinct().sorted(),
                rules = rows.map { it.rule }.distinct().sorted(),
                nowMs = clock,
                latencyByTag = latencyAndNames.first,
                nodeNameByTag = latencyAndNames.second,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConnectionsUiState(),
        )

    fun setOnlyActive(onlyActive: Boolean) {
        filters.value = filters.value.copy(onlyActive = onlyActive)
    }

    fun setApp(app: String?) {
        filters.value = filters.value.copy(app = app)
    }

    fun setNode(node: String?) {
        filters.value = filters.value.copy(node = node)
    }

    fun setRule(rule: String?) {
        filters.value = filters.value.copy(rule = rule)
    }

    internal companion object {
        const val TICK_MS = 1_000L

        /**
         * Pure so the "the filters intersect" rule can be tested without a
         * kernel: a row must satisfy every filter that is set, not any of them.
         */
        fun filterRows(
            rows: List<ConnectionDetail>,
            filters: ConnectionsFilters,
        ): List<ConnectionDetail> = rows.filter { row ->
            (!filters.onlyActive || row.open) &&
                (filters.app == null || row.appPackage == filters.app) &&
                (filters.node == null || row.outbound == filters.node) &&
                (filters.rule == null || row.rule == filters.rule)
        }
    }
}
