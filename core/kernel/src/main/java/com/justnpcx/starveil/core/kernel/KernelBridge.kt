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

    /**
     * Routing decisions the kernel made, pushed as they happen.
     *
     * This is the *actual* path each connection took — which rule matched and
     * which outbound carried it — not a reading of the configured rules. The
     * two disagree precisely when something is wrong, which is the only time
     * anyone looks.
     */
    val connections: SharedFlow<KernelConnection>

    /**
     * Start the kernel with a fully-built config (JSON), or reload the running
     * one with it.
     *
     * Reloading rather than refusing is what makes a policy change take effect
     * without a window: the tun stays up across the swap, so "stop forwarding"
     * and "start forwarding again" never pass through a moment where packets
     * leave directly.
     */
    suspend fun start(config: String): Result<Unit>

    /**
     * Forward nothing.
     *
     * The Kill Switch's block is a *configuration*, not a side effect of the
     * kernel being absent: a config whose only outbound is `block` rejects
     * every connection the tun hands it. This replaces the old trick of
     * closing the kernel and hoping the tun's file descriptor stayed dead —
     * which depended on libbox internals and would turn a block into a silent
     * leak the moment they changed.
     */
    suspend fun block(): Result<Unit>

    /**
     * Forward nothing, without touching the tunnel: stop the kernel and keep
     * the VpnService and its tun up.
     *
     * This is the "block on disconnect" path. Packets still enter the tun —
     * the routes are unchanged — and nothing reads them, so they are dropped.
     * No second kernel is started, which is what made the config-based block
     * race the cache database (`initialize cache-file: timeout`).
     *
     * [start] afterwards reuses the same tun: the service keeps its file
     * descriptor and hands the new kernel a duplicate.
     *
     * The default falls back to [block]: an implementation that cannot keep a
     * tun still blocks, just the expensive way. The real bridge overrides it.
     */
    suspend fun blockKeepingTunnel(): Result<Unit> = block()

    /** Stop the kernel and take the tunnel down. Idempotent. */
    suspend fun stop(): Result<Unit>

    /**
     * Whether the running kernel is the block-only one.
     *
     * A blocking kernel is *running* — it holds the tun and refuses traffic —
     * so [state] alone cannot tell "connected" from "blocked". Everything that
     * needs to know asks this.
     */
    val blocking: StateFlow<Boolean>

    /**
     * Whether a tun device exists right now.
     *
     * Separate from [state] on purpose: the tunnel outlives the kernel. A block
     * keeps it up deliberately, and a process death can leave one behind with
     * nothing attached to it — in both cases `state` says `Stopped` while the
     * VPN interface is still installed and every app is still routed into it.
     */
    suspend fun tunnelPresent(): Boolean = false

    /**
     * Take down a tunnel that no kernel is attached to, and say whether there
     * was one.
     *
     * Called at start-up: the process can be killed and the VpnService brought
     * back by the system, which leaves a fresh process that believes it is
     * disconnected while the interface from the old one is still up. Nothing
     * reads that tun, so every app on the phone is offline — and the UI, which
     * reads [state], says "not connected" and offers to connect.
     *
     * A tunnel with a kernel behind it is never touched: that is a connection,
     * whatever this process remembers.
     */
    suspend fun releaseOrphanTunnel(): Boolean = false

    /** Current traffic snapshot. Returns zeroed stats when not running. */
    suspend fun queryStats(): TrafficStats

    /**
     * The kernel's own version string, e.g. `1.14.3-647c447`.
     *
     * Read from the core rather than hard-coded here: the app ships a built
     * libbox, so a version written into Kotlin would drift from the artifact
     * silently, and the version is exactly what a bug report needs.
     *
     * Safe to call before [start] — the core returns a build constant and its
     * package init needs no setup.
     */
    suspend fun version(): String
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

/**
 * One routing decision, as the kernel reported it.
 *
 * [rule] is the kernel's own rendering of the matched rule (its matcher text),
 * and [outboundType] is the *type* of the outbound that carried it — `direct`,
 * `block`, or a protocol name — not its tag. Both are deliberately passed
 * through unmapped: turning them into app vocabulary is the adapter's job, and
 * this stays a faithful copy of what the kernel said.
 */
data class KernelConnection(
    val id: String,
    val destination: String,
    val domain: String,
    val rule: String,
    val outbound: String,
    val outboundType: String,
    val createdAtMs: Long,
)

/** Traffic snapshot. All values are cumulative bytes + instantaneous rate. */
data class TrafficStats(
    val uplinkBytes: Long = 0L,
    val downlinkBytes: Long = 0L,
    /** Bytes per second, sampled at query time. */
    val uplinkRate: Long = 0L,
    val downlinkRate: Long = 0L,
)
