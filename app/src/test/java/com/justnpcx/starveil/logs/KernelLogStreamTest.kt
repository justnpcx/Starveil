package com.justnpcx.starveil.logs

import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelConnection
import com.justnpcx.starveil.core.kernel.KernelLogLine
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.LogLevel
import com.justnpcx.starveil.core.kernel.TrafficStats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ring gives every line a unique id, and the viewer keys its list on it.
 *
 * This is a regression guard for a real crash: sing-box logs the same text
 * twice inside one millisecond, so a key built from the timestamp and the
 * message collided and the Logs screen died with
 * `IllegalArgumentException: Key "..." was already used`.
 */
class KernelLogStreamTest {

    @Test
    fun `identical lines in the same millisecond get distinct ids`() = runBlocking {
        val bridge = RecordingBridge()
        val stream = KernelLogStream(bridge)

        val line = KernelLogLine(LogLevel.INFO, "sing-box", "same text", timestampMs = 1_000L)
        bridge.emit(line)
        bridge.emit(line)

        val lines = withTimeout(5_000) { stream.lines.first { it.size == 2 } }
        assertEquals(2, lines.size)
        assertEquals(2, lines.map { it.seq }.distinct().size)
    }
}

private class RecordingBridge : KernelBridge {

    // Replayed, so a line emitted before the stream's collector subscribes is
    // still delivered — otherwise this test would race its own setup.
    private val flow = MutableSharedFlow<KernelLogLine>(replay = 16, extraBufferCapacity = 16)

    override val logs: SharedFlow<KernelLogLine> = flow
    override val connections: SharedFlow<KernelConnection> = MutableSharedFlow()
    override val state: StateFlow<KernelState> = MutableStateFlow<KernelState>(KernelState.Stopped)

    fun emit(line: KernelLogLine) {
        flow.tryEmit(line)
    }

    override suspend fun start(config: String): Result<Unit> = Result.success(Unit)
    override suspend fun stop(): Result<Unit> = Result.success(Unit)
    override suspend fun stopKeepingTunnel(): Result<Unit> = Result.success(Unit)
    override suspend fun queryStats(): TrafficStats = TrafficStats()
    override suspend fun version(): String = "test"
}
