package com.justnpcx.starveil.core.kernel

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Fake kernel for UI development and screenshots.
 *
 * Simulates: start/stop latency, state transitions, redacted log lines, and
 * plausible traffic counters that grow while "running". No real networking
 * happens here.
 */
class FakeKernelBridge : KernelBridge {

    private val _state = MutableStateFlow<KernelState>(KernelState.Stopped)
    override val state: StateFlow<KernelState> = _state.asStateFlow()

    // Replay + buffer so log emission never suspends the caller.
    private val _logs = MutableSharedFlow<KernelLogLine>(replay = 64, extraBufferCapacity = 64)
    override val logs = _logs.asSharedFlow()

    private var running = false
    private var uplinkBytes = 0L
    private var downlinkBytes = 0L
    private var lastQueryMs = 0L

    private fun log(level: LogLevel, message: String) {
        // Fake logs are static strings — nothing sensitive can leak.
        // Real implementations MUST redact before emitting (see KernelBridge).
        _logs.tryEmit(KernelLogLine(level = level, tag = "fake-kernel", message = message))
    }

    override suspend fun start(config: String): Result<Unit> {
        if (running) return Result.success(Unit)
        _state.value = KernelState.Starting
        log(LogLevel.INFO, "kernel starting")
        delay(350) // Simulate kernel boot.
        running = true
        lastQueryMs = System.currentTimeMillis()
        _state.value = KernelState.Running
        log(LogLevel.INFO, "kernel running")
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        if (!running) {
            _state.value = KernelState.Stopped
            return Result.success(Unit)
        }
        _state.value = KernelState.Stopping
        log(LogLevel.INFO, "kernel stopping")
        delay(120)
        running = false
        _state.value = KernelState.Stopped
        log(LogLevel.INFO, "kernel stopped")
        return Result.success(Unit)
    }

    override suspend fun queryStats(): TrafficStats {
        if (!running) return TrafficStats()
        val now = System.currentTimeMillis()
        val elapsedSec = ((now - lastQueryMs).coerceAtLeast(0L)) / 1000.0
        lastQueryMs = now
        // Fake but plausible: downstream heavier than upstream.
        val up = (Random.nextLong(20_000, 120_000) * elapsedSec).toLong()
        val down = (Random.nextLong(200_000, 2_000_000) * elapsedSec).toLong()
        uplinkBytes += up
        downlinkBytes += down
        return TrafficStats(
            uplinkBytes = uplinkBytes,
            downlinkBytes = downlinkBytes,
            uplinkRate = if (elapsedSec > 0) (up / elapsedSec).toLong() else 0L,
            downlinkRate = if (elapsedSec > 0) (down / elapsedSec).toLong() else 0L,
        )
    }
}
