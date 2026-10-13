package com.justnpcx.starveil.core.kernel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real kernel bridge — NOT IMPLEMENTED in the skeleton.
 *
 * TODO (Phase 2): wire this to the sing-box libcore:
 * - Load the Go-compiled libbox shared library via JNI.
 * - Translate the app's config model to a sing-box JSON config (pure function,
 *   unit-testable, no IO inside the builder). Prefer the typed
 *   `ProtocolRegistry` descriptors over hand-built JSON.
 * - Run the kernel in a dedicated, supervised process (GuardedProcessPool
 *   pattern: auto-restart on crash, SIGTERM then SIGKILL on stop).
 * - Map queryStats() to the kernel's stats API; map urlTest() to the
 *   kernel's isolated test instance (never the live instance).
 * - Drive `state` from the real process lifecycle; emit `Crashed` with a
 *   typed [KernelError] when the process dies unexpectedly.
 * - NEVER log the config: it contains credentials (see README "安全红线").
 *   Redact before emitting anything to [logs].
 */
class RealKernelBridge : KernelBridge {

    private val _state = MutableStateFlow<KernelState>(KernelState.Stopped)
    override val state: StateFlow<KernelState> = _state.asStateFlow()

    private val _logs = MutableSharedFlow<KernelLogLine>(replay = 64, extraBufferCapacity = 64)
    override val logs = _logs.asSharedFlow()

    override suspend fun start(config: String): Result<Unit> =
        Result.failure(NotImplementedError("RealKernelBridge: sing-box libcore not wired yet (Phase 2)"))

    override suspend fun stop(): Result<Unit> =
        Result.failure(NotImplementedError("RealKernelBridge: sing-box libcore not wired yet (Phase 2)"))

    override suspend fun queryStats(): TrafficStats = TrafficStats()

    override suspend fun urlTest(link: String): UrlTestResult = UrlTestResult.Unreachable

    override suspend fun urlTestBatch(links: List<String>): Map<String, UrlTestResult> =
        links.associateWith { UrlTestResult.Unreachable }
}
