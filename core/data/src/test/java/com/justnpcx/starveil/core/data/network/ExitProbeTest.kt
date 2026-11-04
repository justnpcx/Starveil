package com.justnpcx.starveil.core.data.network

import com.justnpcx.starveil.core.data.log.AppLogSink
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The body comes from someone else's server, so the parser does not assume. */
class ExitProbeTest {

    @Test
    fun `the address is the ip line, not the whole body`() {
        val body = """
            fl=123f45
            h=1.1.1.1
            ip=203.0.113.7
            ts=1700000000.123
            visit_scheme=https
        """.trimIndent()
        assertEquals("203.0.113.7", exitAddressFromTrace(body))
    }

    @Test
    fun `a body without an ip line is nothing, not an empty address`() {
        assertNull(exitAddressFromTrace("fl=123\nh=1.1.1.1"))
        assertNull(exitAddressFromTrace("ip=\n"))
        assertNull(exitAddressFromTrace(""))
    }

    @Test
    fun `an ipv6 address survives the trim`() {
        assertEquals("2001:db8::1", exitAddressFromTrace("ip=2001:db8::1\nts=1"))
    }

    @Test
    fun `a body that is not an address is refused, not echoed`() {
        // The bug this guards: a trace body from a hijacked or broken edge can
        // put anything after ip=. Only a literal is an answer.
        assertNull(exitAddressFromTrace("ip=example.com"))
        assertNull(exitAddressFromTrace("ip=999.1.1.1"))
        assertNull(exitAddressFromTrace("ip=<html>"))
    }

    @Test
    fun `the plain-text endpoint answers with the address alone`() {
        assertEquals("198.51.100.4", exitAddressFromPlain("198.51.100.4\n"))
        assertEquals("198.51.100.4", exitAddressFromPlain("  198.51.100.4  "))
        assertEquals("2001:db8::2", exitAddressFromPlain("2001:db8::2"))
        assertNull(exitAddressFromPlain(""))
        assertNull(exitAddressFromPlain("hello"))
        assertNull(exitAddressFromPlain("198.51.100.4 extra"))
    }

    @Test
    fun `a handshake failure on the first endpoint falls through to the second`() = runBlocking {
        val calls = mutableListOf<String>()
        val probe = object : ExitProbe(AppLogSink()) {
            override suspend fun fetch(url: String): String {
                calls += url
                return when {
                    // The 0.9.20 failure, reproduced: the name does not match
                    // the certificate, so the connection throws.
                    url.contains("www.cloudflare.com") ->
                        throw SSLHandshakeException("certificate does not match")
                    url.contains("1.1.1.1") -> "ip=203.0.113.9\n"
                    else -> throw IOException("should not reach the third")
                }
            }
        }

        val info = probe.probe()

        assertEquals("203.0.113.9", info?.address)
        assertEquals(2, calls.size)
        assertNull(probe.lastFailure.value)
    }

    @Test
    fun `the third endpoint is reached only after the first two fail`() = runBlocking {
        val calls = mutableListOf<String>()
        val probe = object : ExitProbe(AppLogSink()) {
            override suspend fun fetch(url: String): String {
                calls += url
                return when {
                    url.contains("cloudflare.com") -> throw SSLHandshakeException("no")
                    url.contains("1.1.1.1") -> throw SSLHandshakeException("no")
                    else -> "198.51.100.7\n"
                }
            }
        }

        assertEquals("198.51.100.7", probe.probe()?.address)
        assertEquals(3, calls.size)
        assertTrue(calls.last().contains("ipify"))
    }

    @Test
    fun `a chain that fails everywhere returns nothing and says why`() = runBlocking {
        val probe = object : ExitProbe(AppLogSink()) {
            override suspend fun fetch(url: String): String = throw IOException("no route")
        }

        assertNull(probe.probe())
        val failure = probe.lastFailure.value
        assertNotNull(failure)
        assertEquals("IOException", failure?.reason)
        assertTrue(failure?.endpoint.orEmpty().startsWith("https://"))
    }
}
