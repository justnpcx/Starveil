package com.justnpcx.starveil.core.probe

import android.content.Context
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import com.justnpcx.starveil.core.kernel.KernelPaths
import com.justnpcx.starveil.core.kernel.LogRedactor
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.kernel.libbox.StarveilCommandServerHandler
import com.justnpcx.starveil.core.kernel.libbox.StarveilPlatformInterface
import com.justnpcx.starveil.core.model.Profile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.HTTPClient
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Measures a node through its real, fully built detour outbound. */
interface ChainNodeProbe {
    suspend fun probe(exit: Profile, front: Profile, timeoutMs: Int): ProbeResult

    companion object {
        /** Tests of single-hop nodes never call this; production binds the real implementation. */
        val unavailable: ChainNodeProbe = object : ChainNodeProbe {
            override suspend fun probe(exit: Profile, front: Profile, timeoutMs: Int): ProbeResult =
                ProbeResult.ConnectionFailed
        }
    }
}

/**
 * A short-lived, no-tun sing-box service with one loopback SOCKS inbound.
 * ConfigBuilder supplies both protocol fragments and the detour, so the timed
 * request traverses front -> exit instead of a second, approximate chain path.
 */
@Singleton
class SingBoxChainNodeProbe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configBuilder: ConfigBuilder,
) : ChainNodeProbe {
    private val mutex = Mutex()

    override suspend fun probe(exit: Profile, front: Profile, timeoutMs: Int): ProbeResult =
        withContext(Dispatchers.IO) {
            // libbox's setup paths are process-global. Serialising standalone
            // probes keeps batch workers from replacing each other's service.
            mutex.withLock { runProbe(exit, front, timeoutMs) }
        }

    private suspend fun runProbe(exit: Profile, front: Profile, timeoutMs: Int): ProbeResult {
        val paths = KernelPaths(
            basePath = File(context.filesDir, "libbox-chain-probe").absolutePath,
            workingPath = context.filesDir.absolutePath,
            tempPath = context.cacheDir.absolutePath,
        )
        paths.ensureDirectories()
        var server: CommandServer? = null
        var client: HTTPClient? = null
        val cacheName = "cache-chain-probe-${System.nanoTime()}.db"
        var started = 0L
        return try {
            val port = java.net.ServerSocket(0).use { it.localPort }
            val config = configBuilder.buildProbe(exit, front, port, cacheName)
            Libbox.setup(
                SetupOptions().apply {
                    basePath = paths.basePath
                    workingPath = paths.workingPath
                    tempPath = paths.tempPath
                    logMaxLines = 0
                },
            )
            server = Libbox.newCommandServer(
                StarveilCommandServerHandler(),
                StarveilPlatformInterface(context),
            )
            server.start()
            server.startOrReloadService(config, OverrideOptions())
            client = Libbox.newHTTPClient().apply { trySocks5(port) }
            started = System.nanoTime()
            withTimeout(timeoutMs.toLong()) {
                client.newRequest().apply {
                    setURL(PROBE_URL)
                    setMethod("GET")
                }.execute()
            }
            ProbeResult.Reached((System.nanoTime() - started) / 1_000_000)
        } catch (e: TimeoutCancellationException) {
            ProbeResult.TimedOut
        } catch (e: Exception) {
            // libbox reports non-2xx statuses as exceptions. An HTTP response
            // (including cp.cloudflare.com's expected 204) already proves the
            // complete chain answered; it is not a dial failure.
            if (started > 0L && e.message?.matches(Regex("HTTP \\d{3}.*")) == true) {
                return ProbeResult.Reached((System.nanoTime() - started) / 1_000_000)
            }
            KernelDiagnostics.logError("chain probe failed: ${LogRedactor.redact(e.message ?: e.javaClass.simpleName)}")
            val text = e.message.orEmpty().lowercase()
            if (listOf("resolve", "dns", "name resolution").any(text::contains)) {
                ProbeResult.ResolutionFailed
            } else {
                ProbeResult.ConnectionFailed
            }
        } finally {
            runCatching { client?.close() }
            runCatching { server?.closeService() }
            runCatching { server?.close() }
            // Only this probe's own, uniquely named cache files.
            listOf("", "-wal", "-shm").forEach { suffix ->
                File(paths.basePath, cacheName + suffix).delete()
            }
        }
    }

    private companion object {
        const val PROBE_URL = "http://cp.cloudflare.com/"
    }
}
