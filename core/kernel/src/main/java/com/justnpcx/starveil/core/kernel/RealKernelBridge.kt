package com.justnpcx.starveil.core.kernel

import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.kernel.libbox.LogRedactor
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

    private val clientHandler = StarveilCommandClientHandler(
        onConnected = { log(LogLevel.INFO, "kernel connected") },
        onDisconnected = { onCommandChannelDropped() },
        onLog = { line -> _logs.tryEmit(line) },
        onConnection = { connection -> _connections.tryEmit(connection) },
    )

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

    private fun log(level: LogLevel, message: String) {
        _logs.tryEmit(KernelLogLine(level = level, tag = "kernel", message = message))
    }

    override suspend fun start(config: String): Result<Unit> = apply(config, blocking = false)

    override suspend fun block(): Result<Unit> =
        apply(configBuilder.buildBlocking(), blocking = true)

    /**
     * Start, or swap the running kernel's config.
     *
     * The swap is `startOrReloadService` on the live server: the tun, the
     * VpnService and the command channel all stay exactly as they were, so
     * there is no moment between "proxying" and "blocking" in which a packet
     * could leave directly. That gap is the whole reason this is a reload
     * rather than a stop followed by a start.
     */
    private suspend fun apply(config: String, blocking: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            lastConfig = config
            restarted = false
            val current = server ?: return@withContext doStart(config, blocking)
            try {
                current.startOrReloadService(config, OverrideOptions())
                _blocking.value = blocking
                _state.value = KernelState.Running
                log(
                    LogLevel.INFO,
                    if (blocking) "kernel reloaded: forwarding nothing" else "kernel reloaded",
                )
                Result.success(Unit)
            } catch (e: Exception) {
                // The old config is still in force — that is the safer failure:
                // whatever the kernel was doing, it keeps doing. Nothing is
                // torn down, and the caller is told the swap did not happen.
                val detail = LogRedactor.redact(e.message.orEmpty())
                    .ifBlank { e::class.java.simpleName }
                android.util.Log.e(LOG_TAG, "kernel reload failed: $detail")
                log(LogLevel.ERROR, "kernel reload failed: $detail")
                Result.failure(KernelStartException(detail, e))
            }
        }

    private suspend fun doStart(config: String, blocking: Boolean): Result<Unit> {
        _state.value = KernelState.Starting
        log(LogLevel.INFO, "kernel starting")
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
            val newServer = Libbox.newCommandServer(StarveilCommandServerHandler(), platform)
            newServer.start()
            // OverrideOptions must not be null: the Go side dereferences it
            // (command_server.go:214 reads options.AutoRedirect) and a null
            // pointer panics the whole process with SIGABRT.
            newServer.startOrReloadService(config, OverrideOptions())
            server = newServer

            val newClient = Libbox.newCommandClient(clientHandler, clientOptions())
            newClient.connect()
            client = newClient

            _blocking.value = blocking
            _state.value = KernelState.Running
            log(LogLevel.INFO, if (blocking) "kernel running, forwarding nothing" else "kernel running")
            Result.success(Unit)
        } catch (e: Exception) {
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
    override suspend fun stop(): Result<Unit> =
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
    private fun onCommandChannelDropped() {
        if (stopping || server == null) return
        if (!restarted) {
            restarted = true
            log(LogLevel.WARN, "kernel dropped unexpectedly; restarting once")
            scope.launch {
                val wasBlocking = _blocking.value
                teardownQuietly()
                lastConfig?.let { doStart(it, wasBlocking) }
            }
        } else {
            server = null
            client = null
            _state.value = KernelState.Error(KernelError.Crashed("kernel stopped unexpectedly"))
            log(LogLevel.ERROR, "kernel crashed; not restarting again")
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
        if (server == null) TrafficStats() else clientHandler.stats.value

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
