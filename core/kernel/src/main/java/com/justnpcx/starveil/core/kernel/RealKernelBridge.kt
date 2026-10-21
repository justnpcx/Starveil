package com.justnpcx.starveil.core.kernel

import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.kernel.libbox.StarveilCommandClientHandler
import com.justnpcx.starveil.core.kernel.libbox.StarveilCommandServerHandler
import com.justnpcx.starveil.core.kernel.libbox.StarveilPlatformInterface
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real kernel bridge over the sing-box libbox core (CommandServer model — see
 * docs/04-libbox-build.md).
 *
 * Supervision: if the command channel drops while we believe the kernel is
 * running, that is an unexpected death — we restart **once**, and if it drops
 * again we stop and report [KernelError.Crashed] rather than looping forever
 * (PRD K10).
 */
class RealKernelBridge(
    /** Filesystem roots libbox needs; without real paths setup throws. */
    private val paths: KernelPaths,
    /**
     * Builds the block-only config. Owned here rather than passed in as a
     * string so "what does blocking mean" has one answer, in the module that
     * also owns the routing rules.
     */
    private val configBuilder: ConfigBuilder,
    /** Built by :app so it can carry the Context the DNS resolver needs. */
    private val platform: StarveilPlatformInterface,
    private val prepareVpn: () -> Unit = {},
    /**
     * Tears the VpnService down again.
     *
     * Closing the kernel is not enough: the tun's file descriptor belongs to
     * the service (we detached it and handed a copy to the kernel), so the
     * interface stays up, the system keeps showing the VPN icon, and every
     * app's traffic keeps being pulled into a tunnel that no longer forwards
     * anything. Symmetric with [prepareVpn].
     */
    private val stopVpn: () -> Unit = {},
) : KernelBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<KernelState>(KernelState.Stopped)
    override val state: StateFlow<KernelState> = _state.asStateFlow()

    private val _blocking = MutableStateFlow(false)
    override val blocking: StateFlow<Boolean> = _blocking.asStateFlow()

    private val _logs = MutableSharedFlow<KernelLogLine>(replay = 64, extraBufferCapacity = 64)
    override val logs = _logs.asSharedFlow()

    // Replay is small: the flow view wants the last few decisions, not a
    // session's worth, and the adapter keeps its own bounded ring.
    //
    // DROP_OLDEST, not the default: with a full buffer the default policy
    // rejects the *newest* events, which is backwards for a "what just
    // happened" feed — the decisions most worth showing are the ones it would
    // throw away.
    private val _connections = MutableSharedFlow<KernelConnection>(
        replay = 32,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val connections = _connections.asSharedFlow()

    /**
     * The handler of the client that is current, and the generation that
     * identifies it.
     *
     * A handler is created per start rather than once: every deliberate
     * restart disconnects the old client before closing its server, and that
     * callback is indistinguishable from a crash. Told apart by generation,
     * the restart's own drop is ignored — without this the restart is read as
     * an unexpected drop, which schedules a second teardown and start on top
     * of the first. That pair then fails with `initialize cache-file: timeout`
     * while the tunnel from the first half is still up: the UI reports "kernel
     * failed to start" and the VPN is still connected.
     */
    private var clientHandler: StarveilCommandClientHandler? = null
    private var clientGeneration = 0

    private var server: CommandServer? = null
    private var client: CommandClient? = null
    private var lastConfig: String? = null

    init {
        // Kernel-side diagnostics also feed the log ring, so the Logs screen
        // shows them even when adb logcat is unavailable (it is, on some ROMs).
        platform.onDiagnostic = { message -> log(LogLevel.DEBUG, message) }
        // Everything else that has no callback of its own (the VpnService, a
        // rule-set check in :core:data) writes through this, levels included.
        KernelDiagnostics.sink = { message, level -> log(level, message) }
    }

    @Volatile
    private var stopping = false

    @Volatile
    private var restarted = false

    /** Every lifecycle transition runs under this; see [LifecycleGate]. */
    private val lifecycle = LifecycleGate()

    private fun log(level: LogLevel, message: String) {
        _logs.tryEmit(KernelLogLine(level = level, tag = "kernel", message = message))
    }

    override suspend fun start(config: String): Result<Unit> =
        lifecycle.exclusive { applyLocked(config, blocking = false) }

    /**
     * Forward nothing: run the block-only config.
     *
     * The block is a config the kernel enforces, not a tun with no reader.
     * Going there is the same full teardown as any other config change — see
     * [applyLocked] for why a second start has to be preceded by one — which costs a
     * short window with no VPN. That window is the price of the block actually
     * being in force; the alternative, keeping the old tun and closing the
     * kernel, leaves the cache database locked and breaks the next connect.
     */
    override suspend fun block(): Result<Unit> {
        val config = configBuilder.buildBlocking()
        return lifecycle.exclusive { applyLocked(config, blocking = true) }
    }

    /**
     * Blackhole: stop the kernel, keep the tunnel.
     *
     * The VpnService and its tun stay up, so every packet still enters the
     * tunnel; nothing reads it, so everything is dropped. Nothing opens the
     * cache database, which is the whole point — the config-based block had to
     * start a second kernel and lost that race every time on this device.
     */
    override suspend fun blockKeepingTunnel(): Result<Unit> =
        lifecycle.exclusive { blockKeepingTunnelLocked() }

    /**
     * The body of [blockKeepingTunnel], without the gate.
     *
     * Split out rather than locked inline so the public entry point is the only
     * place that takes the gate: a locked method calling another locked method
     * on the same non-reentrant mutex would deadlock.
     */
    private suspend fun blockKeepingTunnelLocked(): Result<Unit> =
        withContext(Dispatchers.IO) {
            val current = server
            if (current == null) {
                // Nothing to stop. If the service still holds a tun, the
                // blackhole is already in force; if it does not, there is no
                // tunnel to keep and saying "blocked" would be a lie.
                val kept = com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService.current
                    ?.hasTun == true
                _blocking.value = kept
                _state.value = KernelState.Stopped
                return@withContext if (kept) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("no tunnel to keep"))
                }
            }

            stopping = true
            _state.value = KernelState.Stopping
            log(LogLevel.INFO, "kernel stopping; tunnel stays up and forwards nothing")
            val result = try {
                runCatching { client?.disconnect() }
                client = null
                current.closeService()
                current.close()
                server = null
                // Deliberately no stopVpn(): the tun is the block.
                _blocking.value = true
                _state.value = KernelState.Stopped
                log(LogLevel.INFO, "kernel stopped; tun kept, forwarding nothing")
                Result.success(Unit)
            } catch (e: Exception) {
                client = null
                server = null
                _blocking.value = false
                _state.value = KernelState.Error(
                    KernelError.Unknown("could not stop the kernel for the block", e),
                )
                log(LogLevel.ERROR, "block: kernel failed to stop: ${e.javaClass.simpleName}")
                Result.failure(e)
            } finally {
                stopping = false
            }
            result
        }

    /**
     * Start, or replace the running kernel's config.
     *
     * Replacing means a full teardown and a fresh start, because that is the
     * only sequence this kernel survives. Measured on device, in this order:
     *
     * - `startOrReloadService` on a live server: the new instance opens the
     *   cache database the old one still holds and dies with
     *   `initialize cache-file: timeout`, after the retry budget, every time.
     *   `cache_file.enabled=false` does not help: the daemon passes itself as
     *   the platform log writer, and that alone forces the cache file on.
     * - Closing the kernel and starting a new one with the VpnService left up:
     *   same failure, same message, even with a per-policy cache path.
     * - Full teardown (kernel and VpnService), wait for the service to be gone,
     *   then start: works, repeatedly.
     *
     * The cost is a short window with no VPN between the two halves, which is
     * why this is only reached when there was going to be a change anyway.
     */
    private suspend fun applyLocked(config: String, blocking: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            lastConfig = config
            restarted = false
            val current = server ?: return@withContext doStart(config, blocking)

            _state.value = KernelState.Starting
            log(LogLevel.INFO, "kernel restarting; tunnel goes down for the swap")
            runCatching { client?.disconnect() }
            client = null
            runCatching { current.closeService() }
            runCatching { current.close() }
            server = null
            stopVpn()
            // The VpnService teardown is asynchronous (stopService -> onDestroy
            // -> closeTun). Starting the new kernel into a service that is
            // still dying is what produced the `initialize cache-file: timeout`
            // failures; waiting for it to be gone makes the halves sequential.
            awaitVpnDown()
            val first = doStart(config, blocking)
            if (first.isSuccess) return@withContext first
            // The Go daemon can outlive its VpnService: the cache database
            // stays locked for a few seconds after the service is gone, and
            // the new kernel dies with `initialize cache-file: timeout`
            // (measured on the vivo: the old daemon took ~9 s to exit after a
            // block-on-disconnect). One retry, after the daemon has had time
            // to leave, is the difference between a real block and a UI that
            // says "blocked" while traffic flows.
            log(LogLevel.WARN, "start failed; retrying once after the old daemon exits")
            kotlinx.coroutines.delay(START_RETRY_DELAY_MS)
            doStart(config, blocking)
        }

    /** Waits until no VpnService instance is left, up to a bounded time. */
    private suspend fun awaitVpnDown() {
        repeat((VP_NETWORK_WAIT_MS / 50).toInt()) {
            if (com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService.current == null) return
            kotlinx.coroutines.delay(50)
        }
    }

    private suspend fun doStart(config: String, blocking: Boolean): Result<Unit> {
        _state.value = KernelState.Starting
        log(LogLevel.INFO, "kernel starting")
        // Held outside the try so a failed start can close what it created.
        // `startOrReloadService` can fail after the daemon has opened the
        // cache database; leaving that instance alive keeps the file locked
        // and makes every retry fail the same way.
        var started: CommandServer? = null
        var startedClient: CommandClient? = null
        return try {
            prepareVpn()
            paths.ensureDirectories()
            Libbox.setup(
                SetupOptions().apply {
                    basePath = paths.basePath
                    workingPath = paths.workingPath
                    tempPath = paths.tempPath
                    fixAndroidStack = true
                    // 0 = no cap on the in-memory kernel log ring.
                    logMaxLines = 0
                },
            )
            started = Libbox.newCommandServer(StarveilCommandServerHandler(), platform)
            started.start()
            // OverrideOptions must not be null: the Go side dereferences it
            // (command_server.go:214 reads options.AutoRedirect) and a null
            // pointer panics the whole process with SIGABRT.
            started.startOrReloadService(config, OverrideOptions())
            server = started

            val generation = ++clientGeneration
            val handler = StarveilCommandClientHandler(
                onConnected = { log(LogLevel.INFO, "kernel connected") },
                onDisconnected = { message -> onCommandChannelDropped(generation, message) },
                onLog = { line -> _logs.tryEmit(line) },
                onConnection = { connection -> _connections.tryEmit(connection) },
            )
            clientHandler = handler
            startedClient = Libbox.newCommandClient(handler, clientOptions())
            startedClient.connect()
            client = startedClient

            _blocking.value = blocking
            _state.value = KernelState.Running
            log(LogLevel.INFO, if (blocking) "kernel running, forwarding nothing" else "kernel running")
            Result.success(Unit)
        } catch (e: Exception) {
            // Close what this attempt created before the caller retries: an
            // unclosed failed server keeps the cache database locked, which
            // is exactly the timeout that got us here.
            runCatching { startedClient?.disconnect() }
            runCatching { started?.closeService() }
            runCatching { started?.close() }
            client = null
            server = null
            // A failed start can still have established a tun; leaving it up
            // would route every app into a tunnel with nothing behind it.
            runCatching { stopVpn() }
            // Redacted: a libbox error can echo back parts of the config.
            val detail = LogRedactor.redact(e.message.orEmpty()).ifBlank { e::class.java.simpleName }
            android.util.Log.e(LOG_TAG, "kernel start failed: $detail")
            _state.value = KernelState.Error(classifyStartFailure(e))
            log(LogLevel.ERROR, "kernel failed to start: $detail")
            // Carry the redacted reason out so the UI can tell the truth
            // (PRD §8.3) instead of a generic "failed to start".
            Result.failure(KernelStartException(detail, e))
        }
    }


    /**
     * Closes the kernel and the tunnel.
     *
     * A tunnel that outlives its kernel leaves the VPN icon in the status bar
     * and routes every app into an interface that forwards nothing — which is
     * why the Kill Switch's block is a *config* now, not this state.
     */
    override suspend fun stop(): Result<Unit> = lifecycle.exclusive { stopLocked() }

    /** The body of [stop], without the gate — see [blockKeepingTunnelLocked]. */
    private suspend fun stopLocked(): Result<Unit> =
        withContext(Dispatchers.IO) {
            val current = server
            if (current == null) {
                // prepareVpn() starts the VpnService *before* the kernel, so
                // the service can be up with a tun even though nothing is
                // running. Returning early here without stopping it is what
                // left the VPN icon in the status bar after a disconnect.
                log(LogLevel.DEBUG, "stop: no kernel running, stopping the VpnService anyway")
                stopVpn()
                _blocking.value = false
                _state.value = KernelState.Stopped
                return@withContext Result.success(Unit) // idempotent
            }
            stopping = true
            _state.value = KernelState.Stopping
            log(LogLevel.INFO, "kernel stopping")
            val result = try {
                client?.let { runCatching { it.disconnect() } }
                client = null
                current.closeService()
                current.close()
                server = null
                log(LogLevel.DEBUG, "stop: kernel closed, stopping the VpnService")
                stopVpn()
                _blocking.value = false
                _state.value = KernelState.Stopped
                log(LogLevel.INFO, "kernel stopped")
                Result.success(Unit)
            } catch (e: Exception) {
                client = null
                server = null
                // Never leave a stray tunnel behind on an error path.
                stopVpn()
                _blocking.value = false
                _state.value = KernelState.Error(KernelError.Unknown("kernel failed to stop cleanly", e))
                Result.failure(e)
            }
            stopping = false
            result
        }

    /** The command channel dropped without us asking it to. */
    private fun onCommandChannelDropped(generation: Int, message: String) {
        // Scheduled, not run inline: this arrives on a libbox callback thread,
        // and the transition it wants is the same one a user action may be
        // running right now. The gate decides the order; the guards are read
        // inside it so a drop that arrives during a deliberate stop sees
        // `stopping` after that stop has set it.
        scope.launch {
            lifecycle.exclusive {
                // A client we already replaced. Every restart closes one on
                // purpose, and reading that as a crash is what produced a
                // second start racing the first.
                if (generation != clientGeneration) {
                    log(LogLevel.DEBUG, "old command client closed; not a crash")
                    return@exclusive
                }
                if (stopping || server == null) return@exclusive
                if (!restarted) {
                    restarted = true
                    log(LogLevel.WARN, "kernel dropped unexpectedly; restarting once")
                    val wasBlocking = _blocking.value
                    teardownQuietly()
                    lastConfig?.let { doStart(it, wasBlocking) }
                } else {
                    server = null
                    client = null
                    _state.value = KernelState.Error(KernelError.Crashed("kernel stopped unexpectedly"))
                    log(LogLevel.ERROR, "kernel crashed; not restarting again: $message")
                }
            }
        }
    }

    private fun teardownQuietly() {
        runCatching { client?.disconnect() }
        client = null
        runCatching { server?.closeService() }
        runCatching { server?.close() }
        server = null
    }

    override suspend fun queryStats(): TrafficStats =
        if (server == null) TrafficStats() else clientHandler?.stats?.value ?: TrafficStats()

    override suspend fun version(): String = Libbox.version()

    /** Maps a start failure to a typed error the UI can branch on. */
    private fun classifyStartFailure(e: Exception): KernelError {
        val message = e.message.orEmpty()
        return when {
            message.contains("permission", ignoreCase = true) ||
                message.contains("consent", ignoreCase = true) ->
                KernelError.PermissionDenied("VPN permission denied", e)

            message.contains("config", ignoreCase = true) ->
                KernelError.ConfigInvalid("kernel rejected the configuration", e)

            else -> KernelError.StartFailed("kernel failed to start", e)
        }
    }

    private companion object {
        const val LOG_TAG = "StarveilKernel"

        /** Upper bound on waiting for the VpnService to finish tearing down. */
        const val VP_NETWORK_WAIT_MS = 2_000L

        /** How long to let a failed start's predecessor finish dying. */
        const val START_RETRY_DELAY_MS = 1_500L

        /**
         * `CommandClient` only subscribes to the streams named in its options
         * (`command_client.go` switches over `options.commands`), so with none
         * named the kernel pushes nothing at all — no traffic stats and no
         * logs. gobind does not export the Go constants, so these are the
         * values from `experimental/libbox/command.go`.
         */
        const val COMMAND_LOG = 0
        const val COMMAND_STATUS = 1

        /**
         * `libbox.CommandConnections` — same reasoning as the two above: the
         * Go constants are not exported, so this is the value from
         * `experimental/libbox/command.go`. Without it the kernel never pushes
         * [writeConnectionEvents], and the flow view has nothing to show.
         */
        const val COMMAND_CONNECTIONS = 4

        fun clientOptions(): CommandClientOptions = CommandClientOptions().apply {
            addCommand(COMMAND_LOG)
            addCommand(COMMAND_STATUS)
            addCommand(COMMAND_CONNECTIONS)
        }
    }
}

/**
 * A kernel start failure whose [message] is already redacted, so it is safe to
 * show to the user. [KernelBridge.start] returns this instead of the raw
 * libbox exception, which may quote the config.
 */
class KernelStartException(message: String, cause: Throwable) : Exception(message, cause)
