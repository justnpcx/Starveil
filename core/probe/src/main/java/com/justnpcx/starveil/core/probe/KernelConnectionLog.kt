package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.kernel.ConnectionEventKind
import com.justnpcx.starveil.core.kernel.ConnectionLog
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.model.ConnectionDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Folds the kernel's three connection events into one row per connection.
 *
 * Lives in :core:probe, the layer allowed to touch the kernel without being the
 * UI. The list is a ring: a tunnel can open thousands of connections an hour,
 * and a diagnostics table that grows without limit is a memory leak with a
 * scrollbar.
 *
 * Open rows come first, newest first; closed rows follow, newest close first.
 * That is the order the two questions are asked in — "what is happening" and
 * "what just happened" — and it also means the ring's cap evicts history
 * before it evicts anything live.
 */
class KernelConnectionLog(bridge: KernelBridge) : ConnectionLog {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ring = MutableStateFlow<List<ConnectionDetail>>(emptyList())

    override val connections: Flow<List<ConnectionDetail>> = ring.asStateFlow()

    init {
        scope.launch {
            bridge.connections.collect { event ->
                ring.update { apply(it, event, System.currentTimeMillis()) }
            }
        }
    }

    internal companion object {
        /**
         * 200 rows: enough for a browsing session's worth of history beside the
         * live table, and small enough that copying the list on every kernel
         * update stays cheap (200 small objects, once a second at most).
         */
        const val RING_SIZE = 200

        /**
         * One event applied to the table.
         *
         * Pure so the two behaviours that matter can be tested without a
         * kernel: an update must replace its row rather than stack a second
         * one, and a close must leave the row in place with a close time —
         * deleting it would make a connection vanish the moment it ended,
         * which reads as "the app lost it".
         *
         * An update or close for a connection the ring has never seen (the
         * screen was opened mid-connection, or the ring evicted it) is treated
         * as a new row: the kernel is the source of truth, and refusing to show
         * something it just told us about would be the worse failure.
         */
        fun apply(
            existing: List<ConnectionDetail>,
            event: KernelConnection,
            nowMs: Long,
        ): List<ConnectionDetail> {
            val previous = existing.firstOrNull { it.id == event.id }
            val row = event.toDetail(previous)
            val next = existing.filterNot { it.id == event.id }
            return order(listOf(row) + next).take(RING_SIZE)
        }

        /** Open first, then by start; closed after, by close time. */
        fun order(rows: List<ConnectionDetail>): List<ConnectionDetail> =
            rows.sortedWith(
                compareByDescending<ConnectionDetail> { it.open }
                    .thenByDescending { if (it.open) it.startedAtMs else it.closedAtMs }
                    .thenByDescending { it.startedAtMs },
            )

        private fun KernelConnection.toDetail(previous: ConnectionDetail?): ConnectionDetail {
            val started = if (createdAtMs > 0L) createdAtMs else previous?.startedAtMs ?: 0L
            // An update carries deltas, not totals: the kernel sends what moved
            // since the last report. A new event carries the totals, and a
            // close may carry either — so take the larger of "the total the
            // kernel stated" and "what we have plus this delta".
            val prevUp = previous?.uplinkBytes ?: 0L
            val prevDown = previous?.downlinkBytes ?: 0L
            val up = when {
                uplinkBytes > 0L -> maxOf(uplinkBytes, prevUp + uplinkDelta)
                uplinkDelta > 0L -> prevUp + uplinkDelta
                else -> prevUp
            }
            val down = when {
                downlinkBytes > 0L -> maxOf(downlinkBytes, prevDown + downlinkDelta)
                downlinkDelta > 0L -> prevDown + downlinkDelta
                else -> prevDown
            }
            val closed = when {
                event == ConnectionEventKind.CLOSE && closedAtMs > 0L -> closedAtMs
                closedAtMs > 0L -> closedAtMs
                else -> previous?.closedAtMs ?: 0L
            }
            return ConnectionDetail(
                id = id,
                appPackage = appPackage.ifBlank { previous?.appPackage.orEmpty() },
                destination = destination.ifBlank { previous?.destination.orEmpty() },
                domain = domain.ifBlank { previous?.domain.orEmpty() },
                rule = rule.ifBlank { previous?.rule.orEmpty() },
                outbound = outbound.ifBlank { previous?.outbound.orEmpty() },
                outboundType = outboundType.ifBlank { previous?.outboundType.orEmpty() },
                startedAtMs = started,
                closedAtMs = closed,
                uplinkBytes = up,
                downlinkBytes = down,
                network = network.ifBlank { previous?.network.orEmpty() },
                protocol = protocol.ifBlank { previous?.protocol.orEmpty() },
            )
        }
    }
}
