package com.justnpcx.starveil.core.kernel

import com.justnpcx.starveil.core.kernel.libbox.StarveilCommandClientHandler
import com.justnpcx.starveil.core.kernel.libbox.StarveilCommandServerHandler
import com.justnpcx.starveil.core.kernel.libbox.StarveilPlatformInterface
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.Libbox
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
    private val platform: StarveilPlatformInterface = StarveilPlatformInterface(),
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
            Libbox.setup(SetupOptions())
            val newServer = Libbox.newCommandServer(StarveilCommandServerHandler(), platform)
            newServer.start()
            newServer.startOrReloadService(config, null)
            server = newServer

            val newClient = Libbox.newCommandClient(clientHandler, CommandClientOptions())
            newClient.connect()
            client = newClient

            _state.value = KernelState.Running
            log(LogLevel.INFO, "kernel running")
            Result.success(Unit)
        } catch (e: Exception) {
            client = null
            server = null
            _state.value = KernelState.Error(classifyStartFailure(e))
            log(LogLevel.ERROR, "kernel failed to start")
            Result.failure(e)
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
        val tag = linkToTag(link) ?: return UrlTestResult.Unreachable
        val active = client ?: return UrlTestResult.Unreachable
        val before = clientHandler.urlTestResults.value[tag]?.testTimeMs ?: 0L
        return try {
            active.urlTest(tag)
            // The result is pushed, not returned: wait for a newer sample.
            val snapshot = withTimeoutOrNull(URL_TEST_TIMEOUT_MS) {
                clientHandler.urlTestResults.map { it[tag] }.first { it != null && it.testTimeMs > before }
            }
            when {
                snapshot == null -> UrlTestResult.Timeout
                snapshot.succeeded -> UrlTestResult.Success(snapshot.delayMs.toLong())
                else -> UrlTestResult.Timeout
            }
        } catch (e: Exception) {
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
    }
}
