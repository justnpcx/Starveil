package com.justnpcx.starveil.core.kernel

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The ONLY contract between the app and the proxy kernel.
 *
 * Architecture rule: `:app` and `:feature:*` modules must never touch the
 * kernel directly — no sing-box imports, no JNI, no process management.
 * Everything goes through this interface, so the kernel can be swapped
 * (Fake <-> real libcore) without touching a single UI file.
 *
 * All functions are suspending and confined to a caller-provided scope.
 * Implementations must never use GlobalScope (see README "并发红线").
 */
interface KernelBridge {

    /**
     * Kernel lifecycle state, pushed — never polled. UI observes this
     * instead of maintaining a parallel copy of kernel state.
     */
    val state: StateFlow<KernelState>

    /**
     * Kernel log lines, pushed. The bridge MUST redact credentials before
     * emitting: no config dumps, no URLs carrying tokens, no UUIDs /
     * passwords / private keys. The log viewer renders this flow.
     */
    val logs: SharedFlow<KernelLogLine>

    /** Start the kernel with a fully-built config (JSON). Idempotent. */
    suspend fun start(config: String): Result<Unit>

    /** Stop the kernel. Idempotent. */
    suspend fun stop(): Result<Unit>

    /** Current traffic snapshot. Returns zeroed stats when not running. */
    suspend fun queryStats(): TrafficStats

    /**
     * Measure round-trip latency for one outbound [link].
     * Never throws; failures are typed [UrlTestResult] variants.
     *
     * BREAKING CHANGE (Phase 2): previously returned `Long` with a `-1`
     * sentinel for failure. The sentinel was a defect (easy to forget to
     * check, conflated timeout with unreachable). No UI code called
     * `urlTest`, so the only updated call sites are the two
     * implementations in this module.
     */
    suspend fun urlTest(link: String): UrlTestResult

    /**
     * Measure latency for many outbounds concurrently. Returns one
     * [UrlTestResult] per link, keyed by the input link. Implementations
     * run the probes concurrently and respect caller cancellation.
     */
    suspend fun urlTestBatch(links: List<String>): Map<String, UrlTestResult>
}

/** Kernel lifecycle. `Error` carries a typed [KernelError]. */
sealed interface KernelState {
    data object Stopped : KernelState
    data object Starting : KernelState
    data object Running : KernelState
    data object Stopping : KernelState
    data class Error(val error: KernelError) : KernelState
}

/**
 * Typed kernel failures. UI branches on the subtype, not on message
 * text: [ConfigInvalid] and [PermissionDenied] need user action;
 * [StartFailed] and [Crashed] are retryable ([recoverable] = true).
 */
sealed interface KernelError {
    val message: String
    val cause: Throwable?
    val recoverable: Boolean

    /** The supplied config is invalid (bad JSON, unknown outbound, …). */
    data class ConfigInvalid(
        override val message: String,
        override val cause: Throwable? = null,
    ) : KernelError {
        override val recoverable: Boolean = false
    }

    /** The kernel process failed to start (binary missing, port in use, …). */
    data class StartFailed(
        override val message: String,
        override val cause: Throwable? = null,
    ) : KernelError {
        override val recoverable: Boolean = true
    }

    /** The kernel was running, then died unexpectedly. */
    data class Crashed(
        override val message: String,
        override val cause: Throwable? = null,
    ) : KernelError {
        override val recoverable: Boolean = true
    }

    /** An OS permission the kernel needs was denied (e.g. VPN consent). */
    data class PermissionDenied(
        override val message: String,
        override val cause: Throwable? = null,
    ) : KernelError {
        override val recoverable: Boolean = false
    }

    /** Anything else. */
    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null,
    ) : KernelError {
        override val recoverable: Boolean = true
    }
}

/** One kernel log line. See [KernelBridge.logs] for the redaction rule. */
data class KernelLogLine(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** Typed result of one url test. No sentinel values. */
sealed interface UrlTestResult {
    /** Reachable; [latencyMs] is the measured round-trip time. */
    data class Success(val latencyMs: Long) : UrlTestResult

    /** No reply within the probe timeout. */
    data object Timeout : UrlTestResult

    /** Connection refused / DNS failure / other hard failure. */
    data object Unreachable : UrlTestResult
}

/** Traffic snapshot. All values are cumulative bytes + instantaneous rate. */
data class TrafficStats(
    val uplinkBytes: Long = 0L,
    val downlinkBytes: Long = 0L,
    /** Bytes per second, sampled at query time. */
    val uplinkRate: Long = 0L,
    val downlinkRate: Long = 0L,
)
