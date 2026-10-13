package com.justnpcx.starveil.core.kernel

import com.justnpcx.starveil.core.kernel.libbox.StarveilCommandClientHandler
import com.justnpcx.starveil.core.kernel.libbox.StarveilCommandServerHandler
import com.justnpcx.starveil.core.kernel.libbox.StarveilPlatformInterface
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Real kernel bridge over the sing-box libbox core.
 *
 * This fork drives the box through the **CommandServer** API (there is no
 * `Libbox.newService` — see docs/04-libbox-build.md):
 *
 * ```
 * Libbox.setup(...) -> newCommandServer(handler, platform) -> start()
 *                   -> startOrReloadService(config, ...)   // start / reload
 *                   -> closeService() / close()            // stop
 * Libbox.newCommandClient(handler, options) -> connect()    // stats / urlTest
 * ```
 *
 * Traffic stats are **pushed** into [StarveilCommandClientHandler] (sing-box
 * has no stats getter), so `queryStats()` reads a cached value rather than
 * blocking — the contract's "pushed, never polled".
 */
class RealKernelBridge(
    private val platform: StarveilPlatformInterface = StarveilPlatformInterface(),
) : KernelBridge {

    private val _state = MutableStateFlow<KernelState>(KernelState.Stopped)
    override val state: StateFlow<KernelState> = _state.asStateFlow()

    private val _logs = MutableSharedFlow<KernelLogLine>(replay = 64, extraBufferCapacity = 64)
    override val logs = _logs.asSharedFlow()

    private val clientHandler = StarveilCommandClientHandler(
        onConnected = { log(LogLevel.INFO, "kernel connected") },
        onDisconnected = { reason -> log(LogLevel.WARN, "kernel disconnected") },
        onLog = { line -> _logs.tryEmit(line) },
    )

    private var server: CommandServer? = null
    private var client: CommandClient? = null

    private fun log(level: LogLevel, message: String) {
        // Static, credential-free strings only; sing-box log lines go through
        // the handler's redactor before reaching the flow.
        _logs.tryEmit(KernelLogLine(level = level, tag = "kernel", message = message))
    }

    override suspend fun start(config: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (server != null) return@withContext Result.success(Unit) // idempotent
        _state.value = KernelState.Starting
        log(LogLevel.INFO, "kernel starting")
        try {
            Libbox.setup(SetupOptions())
            val newServer = Libbox.newCommandServer(StarveilCommandServerHandler(), platform)
            newServer.start()
            newServer.startOrReloadService(config, null)
            server = newServer

            // The command client is the stats/log channel.
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
        _state.value = KernelState.Stopping
        log(LogLevel.INFO, "kernel stopping")
        return@withContext try {
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
    }

    /** Reads the pushed stats; zeroes while the kernel is not running. */
    override suspend fun queryStats(): TrafficStats =
        if (server == null) TrafficStats() else clientHandler.stats.value

    override suspend fun urlTest(link: String): UrlTestResult {
        // TODO(task 1): resolve `link` to an outbound tag, then
        // client.urlTest(tag). The client takes a tag, not a URL, and the
        // result arrives via the handler — so this needs a tag→result bridge
        // before it can return anything but "not measured".
        return UrlTestResult.Unreachable
    }

    override suspend fun urlTestBatch(links: List<String>): Map<String, UrlTestResult> =
        links.associateWith { urlTest(it) }

    /** Maps a start failure to a typed error the UI can branch on. */
    private fun classifyStartFailure(e: Exception): KernelError {
        val message = e.message.orEmpty()
        return when {
            message.contains("permission", ignoreCase = true) ->
                KernelError.PermissionDenied("VPN permission denied", e)

            message.contains("config", ignoreCase = true) ->
                KernelError.ConfigInvalid("kernel rejected the configuration", e)

            else -> KernelError.StartFailed("kernel failed to start", e)
        }
    }
}
