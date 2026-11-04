package com.justnpcx.starveil.feature.settings

import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.TrafficStats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping the connections page depends on: one tap, one kernel call, and a
 * refusal reported as false rather than swallowed.
 */
class ConnectionCloserTest {

    @Test
    fun `a close goes to the kernel with the row's id`() = runTest {
        val kernel = RecordingBridge()
        val closer = ConnectionCloser(kernel)

        assertTrue(closer.close("c-42"))
        assertEquals(listOf("c-42"), kernel.closedIds)
    }

    @Test
    fun `a blank id is refused without calling the kernel`() = runTest {
        val kernel = RecordingBridge()
        val closer = ConnectionCloser(kernel)

        assertFalse(closer.close(""))
        assertTrue(kernel.closedIds.isEmpty())
    }

    @Test
    fun `a kernel refusal is reported as false`() = runTest {
        val kernel = RecordingBridge(fail = true)
        val closer = ConnectionCloser(kernel)

        assertFalse(closer.close("c-1"))
        assertFalse(closer.closeAll())
    }

    @Test
    fun `close all is one call, not a loop of closes`() = runTest {
        val kernel = RecordingBridge()
        val closer = ConnectionCloser(kernel)

        assertTrue(closer.closeAll())
        assertEquals(1, kernel.closeAlls)
        assertTrue(kernel.closedIds.isEmpty())
    }
}

private class RecordingBridge(private val fail: Boolean = false) : KernelBridge {
    val closedIds = mutableListOf<String>()
    var closeAlls = 0
        private set

    override val state: StateFlow<KernelState> = MutableStateFlow(KernelState.Running)
    override val blocking: StateFlow<Boolean> = MutableStateFlow(false)
    override val logs: SharedFlow<KernelLogLine> = MutableSharedFlow()
    override val connections: SharedFlow<KernelConnection> = MutableSharedFlow()

    override suspend fun start(config: String): Result<Unit> = Result.success(Unit)
    override suspend fun block(): Result<Unit> = Result.success(Unit)
    override suspend fun stop(): Result<Unit> = Result.success(Unit)
    override suspend fun queryStats(): TrafficStats = TrafficStats()
    override suspend fun version(): String = "test"

    override suspend fun closeConnection(id: String): Result<Unit> {
        closedIds += id
        return if (fail) Result.failure(IllegalStateException("refused")) else Result.success(Unit)
    }

    override suspend fun closeConnections(): Result<Unit> {
        closeAlls++
        return if (fail) Result.failure(IllegalStateException("refused")) else Result.success(Unit)
    }
}
