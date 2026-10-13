package com.justnpcx.starveil.core.kernel

import com.justnpcx.starveil.core.kernel.libbox.StarveilCommandServerHandler
import com.justnpcx.starveil.core.kernel.libbox.StarveilPlatformInterface
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
 * ```
 *
 * Implemented: lifecycle + typed state/errors, log redaction, idempotency.
 * Not yet implemented (explicitly, not faked): traffic stats and urlTest need
 * a `CommandClient`; the tun inbound needs a VpnService. Both are marked with
 * TODO and return honest zero/unreachable values meanwhile.
 */
class RealKernelBridge(
    private val platform: StarveilPlatformInterface = StarveilPlatformInterface(),
) : KernelBridge {

    private val _state = MutableStateFlow<KernelState>(KernelState.Stopped)
    override val state: StateFlow<KernelState> = _state.asStateFlow()

    private val _logs = MutableSharedFlow<KernelLogLine>(replay = 64, extraBufferCapacity = 64)
    override val logs = _logs.asSharedFlow()

    private var server: CommandServer? = null

    private fun log(level: LogLevel, message: String) {
        // Static, credential-free strings only. Anything derived from the
        // config or a subscription must be redacted before it gets here.
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
            _state.value = KernelState.Running
            log(LogLevel.INFO, "kernel running")
            Result.success(Unit)
        } catch (e: Exception) {
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
            current.closeService()
            current.close()
            server = null
            _state.value = KernelState.Stopped
            log(LogLevel.INFO, "kernel stopped")
            Result.success(Unit)
        } catch (e: Exception) {
            server = null
            _state.value = KernelState.Error(KernelError.Unknown("kernel failed to stop cleanly", e))
            Result.failure(e)
        }
    }

    override suspend fun queryStats(): TrafficStats {
        // TODO(task 1): read traffic counters from a CommandClient. Returning
        // zeroes (not fabricated numbers) until that is wired.
        return TrafficStats()
    }

    override suspend fun urlTest(link: String): UrlTestResult {
        // TODO(task 1): resolve `link` to an outbound tag and run the kernel's
        // isolated url test (CommandClient.URLTest takes a tag, not a URL).
        // Unreachable is the honest "not measured" answer, not a fake latency.
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
