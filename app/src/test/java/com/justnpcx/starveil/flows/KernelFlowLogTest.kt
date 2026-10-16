package com.justnpcx.starveil.flows

import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.model.FlowOutcome
import com.justnpcx.starveil.core.model.FlowRecord
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The adapter's jobs beyond mapping: a connection appears once, and the list
 * cannot grow without limit.
 *
 * Both are about the kernel's behaviour rather than ours — it reports the same
 * connection again whenever it carries traffic, and it re-sends the whole set
 * after a reset — so without the dedupe and the ring a busy tunnel would fill
 * the flow view with copies of itself.
 */
class KernelFlowLogTest {

    @Test
    fun `a connection the kernel repeats does not stack up`() {
        val once = KernelFlowLog.prepend(emptyList(), record(id = "a"))
        val twice = KernelFlowLog.prepend(once, record(id = "a"))

        assertEquals(1, twice.size)
    }

    @Test
    fun `the ring keeps the newest and drops the oldest`() {
        // Driven through `prepend` rather than the collector on purpose: a
        // burst big enough to fill the ring is exactly when the collector
        // falls behind, so going through it would test the machine's timing
        // rather than the ring.
        val records = (0 until KernelFlowLog.RING_SIZE + 5).fold(emptyList<FlowRecord>()) { acc, i ->
            KernelFlowLog.prepend(acc, record(id = "c$i"))
        }

        assertEquals(KernelFlowLog.RING_SIZE, records.size)
        assertEquals("c${KernelFlowLog.RING_SIZE + 4}", records.first().connectionId)
        assertFalse(records.any { it.connectionId == "c0" })
    }

    @Test
    fun `records reach the flow, newest first`() = runBlocking {
        val bridge = RecordingBridge()
        val log = KernelFlowLog(bridge)

        bridge.emit(connection(id = "a", domain = "example.com"))
        bridge.emit(connection(id = "b", domain = "other.example"))

        val records = withTimeout(5_000) { log.records.first { it.size == 2 } }

        assertEquals(listOf("b", "a"), records.map { it.connectionId })
    }

    @Test
    fun `the kernel's outbound type becomes the app's outcome`() = runBlocking {
        val bridge = RecordingBridge()
        val log = KernelFlowLog(bridge)

        bridge.emit(connection(id = "d", outboundType = "direct"))
        bridge.emit(connection(id = "p", outboundType = "vless"))
        bridge.emit(connection(id = "b", outboundType = "block"))

        val records = withTimeout(5_000) { log.records.first { it.size == 3 } }
        val byId = records.associateBy { it.connectionId }

        assertEquals(FlowOutcome.DIRECT, byId.getValue("d").outcome)
        assertEquals(FlowOutcome.PROXY, byId.getValue("p").outcome)
        assertEquals(FlowOutcome.BLOCK, byId.getValue("b").outcome)
    }
}

private fun record(id: String) = FlowRecord(
    connectionId = id,
    destination = "1.2.3.4:443",
    domain = "",
    rule = "",
    outcome = FlowOutcome.PROXY,
    createdAtMs = 0L,
)

private fun connection(
    id: String,
    domain: String = "",
    outboundType: String = "vless",
) = KernelConnection(
    id = id,
    destination = "1.2.3.4:443",
    domain = domain,
    rule = "domain_suffix=[example.com]",
    outbound = "proxy",
    outboundType = outboundType,
    createdAtMs = 1_700_000_000_000L,
)

private class RecordingBridge : KernelBridge {
    // DROP_OLDEST to mirror the real bridge: with the default policy a full
    // buffer rejects the newest events, which is the opposite of what a
    // "recent decisions" feed wants.
    private val flow = MutableSharedFlow<KernelConnection>(
        replay = 16,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val state: StateFlow<KernelState> = MutableStateFlow(KernelState.Stopped)
    override val logs: SharedFlow<KernelLogLine> = MutableSharedFlow()
    override val connections: SharedFlow<KernelConnection> = flow

    fun emit(connection: KernelConnection) {
        flow.tryEmit(connection)
    }

    override suspend fun start(config: String): Result<Unit> = Result.success(Unit)
    override suspend fun stop(): Result<Unit> = Result.success(Unit)
    override suspend fun stopKeepingTunnel(): Result<Unit> = Result.success(Unit)
    override suspend fun queryStats(): TrafficStats = TrafficStats()
    override suspend fun version(): String = "test"
}
