package com.justnpcx.starveil.core.kernel

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    /** Built by :app so it can carry the Context the DNS resolver needs. */
    private val platform: StarveilPlatformInterface,
    private val prepareVpn: () -> Unit = {},
    /**
     * Maps a share link to the outbound tag [ConfigBuilder] assigned it.
     * `CommandClient.urlTest` takes a tag, not a URL, so without this mapping
     * the bridge cannot honestly test a link — it reports Unreachable rather
     * than guessing.
     */
    private val linkToTag: (String) -> String? = { null },
) : KernelBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<KernelState>(KernelState.Stopped)
    override val state: StateFlow<KernelState> = _state.asStateFlow()

    private val _logs = MutableSharedFlow<KernelLogLine>(replay = 64, extraBufferCapacity = 64)
    override val logs = _logs.asSharedFlow()

    private val clientHandler = StarveilCommandClientHandler(
        onConnected = { log(LogLevel.INFO, "kernel connected") },
        onDisconnected = { onCommandChannelDropped() },
        onLog = { line -> _logs.tryEmit(line) },
    )

    private var server: CommandServer? = null
    private var client: CommandClient? = null
    private var lastConfig: String? = null

    init {
        // Kernel-side diagnostics also feed the log ring, so the Logs screen
        // shows them even when adb logcat is unavailable (it is, on some ROMs).
        platform.onDiagnostic = { message -> log(LogLevel.DEBUG, message) }
    }

    @Volatile
    private var stopping = false

    @Volatile
    private var restarted = false

    private fun log(level: LogLevel, message: String) {
        _logs.tryEmit(KernelLogLine(level = level, tag = "kernel", message = message))
    }

    override suspend fun start(config: String): Result<Unit> = withContext(Dispatchers.IO) {
        lastConfig = config
        restarted = false
        doStart(config)
    }

    private suspend fun doStart(config: String): Result<Unit> {
        if (server != null) return Result.success(Unit) // idempotent
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

            _state.value = KernelState.Running
            log(LogLevel.INFO, "kernel running")
            Result.success(Unit)
        } catch (e: Exception) {
            client = null
            server = null
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

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        val current = server
        if (current == null) {
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
            _state.value = KernelState.Stopped
            log(LogLevel.INFO, "kernel stopped")
            Result.success(Unit)
        } catch (e: Exception) {
            client = null
            server = null
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
                teardownQuietly()
                lastConfig?.let { doStart(it) }
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

    override suspend fun urlTest(link: String): UrlTestResult {
        val tag = linkToTag(link) ?: return UrlTestResult.Unreachable.also {
            android.util.Log.w(LOG_TAG, "urlTest: no tag mapping for the argument")
        }
        val active = client ?: return UrlTestResult.Unreachable.also {
            android.util.Log.w(LOG_TAG, "urlTest($tag): command client is null (kernel not running)")
        }
        val before = clientHandler.urlTestResults.value[tag]?.testTimeMs ?: 0L
        return try {
            active.urlTest(tag)
            android.util.Log.i(LOG_TAG, "urlTest($tag): requested, waiting for a pushed sample")
            // The result is pushed, not returned: wait for a newer sample.
            val snapshot = withTimeoutOrNull(URL_TEST_TIMEOUT_MS) {
                clientHandler.urlTestResults.map { it[tag] }.first { it != null && it.testTimeMs > before }
            }
            when {
                snapshot == null -> {
                    android.util.Log.w(LOG_TAG, "urlTest($tag): no sample within ${URL_TEST_TIMEOUT_MS}ms")
                    UrlTestResult.Timeout
                }

                snapshot.succeeded -> {
                    android.util.Log.i(LOG_TAG, "urlTest($tag): ${snapshot.delayMs} ms")
                    UrlTestResult.Success(snapshot.delayMs.toLong())
                }

                else -> {
                    android.util.Log.w(LOG_TAG, "urlTest($tag): kernel reported delay 0 (failed probe)")
                    UrlTestResult.Timeout
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(LOG_TAG, "urlTest($tag): threw ${e::class.simpleName}")
            UrlTestResult.Unreachable
        }
    }

    override suspend fun urlTestBatch(links: List<String>): Map<String, UrlTestResult> =
        links.associateWith { urlTest(it) }

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
        const val URL_TEST_TIMEOUT_MS = 5_000L
        const val LOG_TAG = "StarveilKernel"

        /**
         * `CommandClient` only subscribes to the streams named in its options
         * (`command_client.go` switches over `options.commands`), so with none
         * named the kernel pushes nothing at all: no traffic stats and no
         * urlTest results. gobind does not export the Go constants, so these
         * are the values from `experimental/libbox/command.go`.
         */
        const val COMMAND_LOG = 0
        const val COMMAND_STATUS = 1
        const val COMMAND_OUTBOUNDS = 5

        fun clientOptions(): CommandClientOptions = CommandClientOptions().apply {
            addCommand(COMMAND_LOG)
            addCommand(COMMAND_STATUS)
            addCommand(COMMAND_OUTBOUNDS)
        }
    }
}

/**
 * A kernel start failure whose [message] is already redacted, so it is safe to
 * show to the user. [KernelBridge.start] returns this instead of the raw
 * libbox exception, which may quote the config.
 */
class KernelStartException(message: String, cause: Throwable) : Exception(message, cause)
