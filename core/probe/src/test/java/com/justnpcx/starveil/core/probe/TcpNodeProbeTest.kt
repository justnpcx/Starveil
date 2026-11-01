package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.data.geo.DohResolver
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the probe's address comes from.
 *
 * The handshake itself is real — a listening socket on loopback — so the test
 * covers the path that matters: a name is resolved over DoH, a literal is not
 * resolved at all, and a failed lookup is a failed probe rather than a
 * cleartext question to the local resolver.
 */
class TcpNodeProbeTest {

    @Test
    fun `a name is resolved over DoH and the handshake still succeeds`() = runBlocking {
        val fetches = AtomicInteger()
        val resolver = DohResolver(
            endpoints = listOf("https://doh/dns-query"),
            fetch = { _, _ ->
                fetches.incrementAndGet()
                response("127.0.0.1")
            },
            fallback = { error("the system resolver must not be asked") },
        )
        val probe = TcpNodeProbe(resolver)

        listening().use { server ->
            val result = probe.probe("node.example", server.localPort, timeoutMs = 2_000)

            assertTrue("expected a completed handshake, got $result", result is ProbeResult.Reached)
            assertEquals(1, fetches.get())
        }
    }

    @Test
    fun `a literal address never asks the resolver`() = runBlocking {
        val fetches = AtomicInteger()
        val resolver = DohResolver(
            fetch = { _, _ -> fetches.incrementAndGet(); ByteArray(0) },
            fallback = { null },
        )
        val probe = TcpNodeProbe(resolver)

        listening().use { server ->
            val result = probe.probe("127.0.0.1", server.localPort, timeoutMs = 2_000)

            assertTrue("expected a completed handshake, got $result", result is ProbeResult.Reached)
            assertEquals(0, fetches.get())
        }
    }

    @Test
    fun `a failed lookup fails the probe instead of falling back to plaintext`() = runBlocking {
        val fallbacks = AtomicInteger()
        val resolver = DohResolver(
            endpoints = listOf("https://doh/dns-query"),
            fetch = { _, _ -> throw java.io.IOException("blocked") },
            fallback = { fallbacks.incrementAndGet(); "127.0.0.1" },
        )
        val probe = TcpNodeProbe(resolver)

        assertEquals(ProbeResult.ResolutionFailed, probe.probe("node.example", 443, timeoutMs = 500))
        assertEquals("the system resolver was used", 0, fallbacks.get())
    }

    @Test
    fun `resolution is outside the measured handshake`() = runBlocking {
        // The lookup is deliberately slow; the reported latency must not
        // include it, or every node behind a distant DoH resolver would look
        // like a bad node.
        val resolver = DohResolver(
            endpoints = listOf("https://doh/dns-query"),
            fetch = { _, _ ->
                delay(300)
                response("127.0.0.1")
            },
            fallback = { null },
        )
        val probe = TcpNodeProbe(resolver)

        listening().use { server ->
            val result = probe.probe("node.example", server.localPort, timeoutMs = 2_000)

            val reached = result as ProbeResult.Reached
            assertTrue("latency ${reached.latencyMs} ms includes the lookup", reached.latencyMs < 250)
        }
    }

    private fun listening(): ServerSocket =
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))

    /** A minimal successful answer for `node.example` with one A record. */
    private fun response(address: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0))
        out.write(4); out.write("node".toByteArray())
        out.write(7); out.write("example".toByteArray())
        out.write(0)
        out.write(byteArrayOf(0, 1, 0, 1)) // qtype A, qclass IN
        out.write(byteArrayOf(0xC0.toByte(), 0x0C)) // name: pointer to offset 12
        out.write(byteArrayOf(0, 1, 0, 1)) // type A, class IN
        out.write(byteArrayOf(0, 0, 0, 0x3C)) // ttl 60
        val rdata = address.split('.').map { it.toInt().toByte() }.toByteArray()
        out.write(byteArrayOf(0, rdata.size.toByte()))
        out.write(rdata)
        return out.toByteArray()
    }
}
