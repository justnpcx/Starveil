package com.justnpcx.starveil.core.probe

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How a probe ended.
 *
 * The timeout/unreachable split is kept because the manual latency button
 * reports them differently ("timed out" versus "unreachable"), and collapsing
 * them would cost that wording. The health store only needs "did not answer",
 * and the caller maps both to [com.justnpcx.starveil.core.model.LATENCY_UNREACHABLE].
 */
sealed interface ProbeResult {
    /** The handshake completed; [latencyMs] is the round-trip. */
    data class Reached(val latencyMs: Long) : ProbeResult

    /** The node never answered inside the probe window. */
    data object TimedOut : ProbeResult

    /** The name did not resolve, or the connection was refused outright. */
    data object Failed : ProbeResult
}

/**
 * Times one TCP handshake to a node.
 *
 * A plain handshake straight to `server:port` is what makes this usable: it
 * needs no tunnel, so it works before connecting and for whichever node is
 * being probed — not only the one already connected. (Our own traffic is
 * excluded from our tunnel, so a probe made while connected still leaves by
 * the physical interface and measures the same path.)
 *
 * What it therefore measures is the network round-trip to the node, not the
 * proxy's own handshake: a node that accepts TCP but rejects the protocol
 * still reads as fast. Proving the protocol works is what connecting is for.
 */
interface NodeProbe {
    /**
     * [timeoutMs] is the handshake budget. The manual button uses the short
     * default — someone is watching it — while the background sweep gives a
     * slow node the full five seconds, because there it is one of hundreds and
     * a false "down" costs more than a slow answer.
     */
    suspend fun probe(server: String, port: Int, timeoutMs: Int = DEFAULT_TIMEOUT_MS): ProbeResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 3_000

        /** The background sweep's budget. */
        const val BATCH_TIMEOUT_MS = 5_000
    }
}

class TcpNodeProbe @Inject constructor() : NodeProbe {

    /**
     * The name is resolved first and outside the clock, so the number reported
     * is the network round-trip rather than however long DNS happened to take.
     */
    override suspend fun probe(server: String, port: Int, timeoutMs: Int): ProbeResult =
        withContext(Dispatchers.IO) {
            val address = try {
                InetAddress.getByName(server)
            } catch (e: UnknownHostException) {
                return@withContext ProbeResult.Failed
            }
            val socket = Socket()
            try {
                val started = System.nanoTime()
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                ProbeResult.Reached((System.nanoTime() - started) / 1_000_000)
            } catch (e: SocketTimeoutException) {
                ProbeResult.TimedOut
            } catch (e: IOException) {
                ProbeResult.Failed
            } finally {
                runCatching { socket.close() }
            }
        }

}
