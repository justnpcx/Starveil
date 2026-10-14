package com.justnpcx.starveil.latency

import android.content.Context
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Measures the latency of the currently selected node and stores it.
 *
 * The probe is a plain TCP handshake straight to the node's `server:port`. That
 * is what makes it usable: it needs no tunnel, so it works before connecting
 * and for whichever node is selected — not only the one already connected.
 * (Our own traffic is excluded from our tunnel, so a probe made while connected
 * still leaves by the physical interface and measures the same path.)
 *
 * What it therefore measures is the network round-trip to the node, not the
 * proxy's own handshake: a node that accepts TCP but rejects the protocol still
 * reads as fast. Proving the protocol works is what connecting is for.
 *
 * Lives in :app so the navigation layer can trigger it; the Nodes screen only
 * receives a callback.
 *
 * Always returns a user-facing message, so a failed measurement is never
 * silent — "nothing happened" was the previous behaviour and read as a broken
 * button.
 */
@Singleton
class NodeLatencyTester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
) {

    suspend fun testSelectedNode(): String {
        val id = preferences.selectedProfileId.first()
        if (id == 0L) return context.getString(R.string.latency_no_node)
        val profile = profiles.getById(id) ?: return context.getString(R.string.latency_no_node)

        return when (val probe = measure(profile.server, profile.port)) {
            is Probe.Reached -> {
                profiles.upsert(profile.copy(latencyMs = probe.latencyMs))
                context.getString(R.string.latency_measured, probe.latencyMs)
            }

            Probe.TimedOut -> context.getString(R.string.latency_timeout)
            Probe.Failed -> context.getString(R.string.latency_unreachable)
        }
    }

    /**
     * Time one TCP handshake to [server]:[port].
     *
     * The name is resolved first and outside the clock, so the number reported
     * is the network round-trip rather than however long DNS happened to take.
     */
    private suspend fun measure(server: String, port: Int): Probe = withContext(Dispatchers.IO) {
        val address = try {
            InetAddress.getByName(server)
        } catch (e: UnknownHostException) {
            return@withContext Probe.Failed
        }
        val socket = Socket()
        try {
            val started = System.nanoTime()
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
            Probe.Reached((System.nanoTime() - started) / 1_000_000)
        } catch (e: SocketTimeoutException) {
            Probe.TimedOut
        } catch (e: IOException) {
            Probe.Failed
        } finally {
            runCatching { socket.close() }
        }
    }

    private sealed interface Probe {
        /** Handshake completed; [latencyMs] is the round-trip. */
        data class Reached(val latencyMs: Long) : Probe

        /** The node never answered inside the probe window. */
        data object TimedOut : Probe

        /** The name did not resolve, or the connection was refused outright. */
        data object Failed : Probe
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000
    }
}

/** Hilt bridge for the Composable that needs it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NodeLatencyTesterEntryPoint {
    fun nodeLatencyTester(): NodeLatencyTester
}
