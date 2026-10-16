package com.justnpcx.starveil.flows

import com.justnpcx.starveil.core.kernel.FlowLog
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.model.FlowOutcome
import com.justnpcx.starveil.core.model.FlowRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Adapts the kernel's connection flow to the UI's [FlowLog].
 *
 * Lives in :app because it is the only layer allowed to touch the kernel. It
 * maps the kernel's vocabulary onto the app's and keeps a bounded ring, so a
 * busy tunnel cannot grow the list without limit.
 */
class KernelFlowLog(bridge: KernelBridge) : FlowLog {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ring = MutableStateFlow<List<FlowRecord>>(emptyList())

    override val records: Flow<List<FlowRecord>> = ring.asStateFlow()

    init {
        scope.launch {
            bridge.connections.collect { connection ->
                val record = connection.toFlowRecord()
                ring.update { prepend(it, record) }
            }
        }
    }

    private fun KernelConnection.toFlowRecord() = FlowRecord(
        connectionId = id,
        destination = destination,
        domain = domain,
        rule = rule,
        outcome = FlowOutcome.fromOutboundType(outboundType),
        createdAtMs = createdAtMs,
    )

    internal companion object {
        /**
         * Enough to see a pattern — a run of blocked hosts, a domain that is
         * not taking the rule it looks like it should — without holding a
         * session's worth of browsing history in memory.
         */
        const val RING_SIZE = 100

        /**
         * Newest first, one entry per connection, bounded.
         *
         * Pure so the two behaviours that matter can be tested without racing
         * the collector: a connection the kernel reports again must not stack
         * up as a second row, and the list must not grow without limit.
         */
        fun prepend(existing: List<FlowRecord>, record: FlowRecord): List<FlowRecord> =
            (listOf(record) + existing.filterNot { it.connectionId == record.connectionId })
                .take(RING_SIZE)
    }
}
