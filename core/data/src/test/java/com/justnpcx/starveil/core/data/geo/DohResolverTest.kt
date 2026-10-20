package com.justnpcx.starveil.core.data.geo

import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DoH client's two halves: the bytes it sends and the bytes it can read.
 *
 * The query fixture is the one used to check the endpoint by hand —
 * `curl 'https://223.5.5.5/dns-query?dns=AAABAAABAAAAAAAAA3d3dwdleGFtcGxlA2NvbQAAAQAB'`
 * answers with an A record, so if this encoder produces those bytes it
 * produces a query a real resolver accepts.
 */
class DohResolverTest {

    @Test
    fun `the query is the wire format a resolver accepts`() {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(DnsMessage.query("www.example.com", id = 0))

        assertEquals("AAABAAABAAAAAAAAA3d3dwdleGFtcGxlA2NvbQAAAQAB", encoded)
    }

    @Test
    fun `the first A record is read through a compression pointer`() {
        assertEquals("93.184.216.34", DnsMessage.firstAddress(response()))
    }

    @Test
    fun `a response with no A record is null`() {
        // AAAA only: the GeoIP database is IPv4, so this is "no answer" here.
        assertNull(DnsMessage.firstAddress(response(type = 28, address = null)))
    }

    @Test
    fun `an error response is null`() {
        val nxdomain = response().copyOf()
        nxdomain[3] = 0x03 // RCODE 3, NXDOMAIN
        assertNull(DnsMessage.firstAddress(nxdomain))
    }

    @Test
    fun `garbage is null, not a crash`() {
        assertNull(DnsMessage.firstAddress(ByteArray(0)))
        assertNull(DnsMessage.firstAddress(byteArrayOf(1, 2, 3)))
        assertNull(DnsMessage.firstAddress(ByteArray(40)))
    }

    @Test
    fun `a literal address needs no request`() = runBlocking {
        var calls = 0
        val resolver = DohResolver(fetch = { _, _ -> calls++; ByteArray(0) })

        assertEquals("192.168.1.1", resolver.resolve("192.168.1.1"))
        assertEquals(0, calls)
    }

    @Test
    fun `every endpoint failing falls back to the system resolver`() = runBlocking {
        val warnings = mutableListOf<String>()
        val resolver = DohResolver(
            endpoints = listOf("https://one/dns-query", "https://two/dns-query"),
            fetch = { _, _ -> throw java.io.IOException("blocked") },
            fallback = { "10.0.0.1" },
            warn = { warnings += it },
        )

        assertEquals("10.0.0.1", resolver.resolve("node.example"))
        // Both endpoints were tried, for both families, and the fallback is
        // announced rather than silent: a cleartext lookup is a decision the
        // log should carry.
        assertEquals(4, warnings.count { it.contains("failed") })
        assertTrue(warnings.any { it.contains("falling back") })
    }

    @Test
    fun `an endpoint without an A record moves on to the next one`() = runBlocking {
        var calls = 0
        val resolver = DohResolver(
            endpoints = listOf("https://one/dns-query", "https://two/dns-query"),
            fetch = { _, _ ->
                calls++
                if (calls == 1) ByteArray(12) else response()
            },
            fallback = { null },
        )

        assertEquals("93.184.216.34", resolver.resolve("www.example.com"))
        assertEquals(2, calls)
    }

    @Test
    fun `a name with only an AAAA record resolves on the second question`() = runBlocking {
        // A node's own domain can have no A at all — a home server on a
        // dual-stack line is the ordinary case. Asking A alone made the probe
        // call a live node dead, and left the kernel with nothing to dial.
        val asked = mutableListOf<Int>()
        val resolver = DohResolver(
            endpoints = listOf("https://one/dns-query"),
            fetch = { url, _ ->
                val query = Base64.getUrlDecoder().decode(url.substringAfter("dns="))
                val qtype = ((query[query.size - 4].toInt() and 0xFF) shl 8) or
                    (query[query.size - 3].toInt() and 0xFF)
                asked += qtype
                if (qtype == DnsMessage.TYPE_AAAA) aaaaResponse() else ByteArray(12)
            },
            fallback = { null },
        )

        assertEquals(
            "2408:8256:2284:20a5:56d1:96ff:fed5:d317",
            resolver.resolve("www.example.com"),
        )
        // A first, then AAAA: the common case must not pay for the fallback.
        assertEquals(listOf(DnsMessage.TYPE_A, DnsMessage.TYPE_AAAA), asked)
    }

    @Test
    fun `an A answer stops before the second question`() = runBlocking {
        var calls = 0
        val resolver = DohResolver(
            endpoints = listOf("https://one/dns-query"),
            fetch = { _, _ -> calls++; response() },
            fallback = { null },
        )

        assertEquals("93.184.216.34", resolver.resolve("www.example.com"))
        assertEquals(1, calls)
    }

    @Test
    fun `an IPv6 literal needs no request`() = runBlocking {
        var calls = 0
        val resolver = DohResolver(fetch = { _, _ -> calls++; ByteArray(0) })

        assertEquals("::1", resolver.resolveStrict("::1"))
        assertEquals(0, calls)
    }

    @Test
    fun `the AAAA query asks for type 28 and is otherwise the same message`() {
        val a = DnsMessage.query("www.example.com")
        val aaaa = DnsMessage.query("www.example.com", type = DnsMessage.TYPE_AAAA)

        assertEquals(a.size, aaaa.size)
        val qtype = ((aaaa[aaaa.size - 4].toInt() and 0xFF) shl 8) or
            (aaaa[aaaa.size - 3].toInt() and 0xFF)
        assertEquals(DnsMessage.TYPE_AAAA, qtype)
        // Everything before the question type is identical.
        assertTrue(a.copyOfRange(0, a.size - 4).contentEquals(aaaa.copyOfRange(0, aaaa.size - 4)))
    }

    /** A minimal successful answer for `www.example.com` with one A record. */
    private fun response(type: Int = 1, address: String? = "93.184.216.34"): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0))
        out.write(3); out.write("www".toByteArray())
        out.write(7); out.write("example".toByteArray())
        out.write(3); out.write("com".toByteArray())
        out.write(0)
        out.write(byteArrayOf(0, 1, 0, 1)) // qtype A, qclass IN
        out.write(byteArrayOf(0xC0.toByte(), 0x0C)) // name: pointer to offset 12
        out.write(byteArrayOf(0, type.toByte(), 0, 1)) // type, class
        out.write(byteArrayOf(0, 0, 0, 0x3C)) // ttl 60
        val rdata = address?.split('.')?.map { it.toInt().toByte() }?.toByteArray() ?: ByteArray(16)
        out.write(byteArrayOf(0, rdata.size.toByte()))
        out.write(rdata)
        return out.toByteArray()
    }

    /** A minimal successful answer carrying one AAAA record. */
    private fun aaaaResponse(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0))
        out.write(3); out.write("www".toByteArray())
        out.write(7); out.write("example".toByteArray())
        out.write(3); out.write("com".toByteArray())
        out.write(0)
        out.write(byteArrayOf(0, 28, 0, 1)) // qtype AAAA, qclass IN
        out.write(byteArrayOf(0xC0.toByte(), 0x0C))
        out.write(byteArrayOf(0, 28, 0, 1)) // type AAAA, class
        out.write(byteArrayOf(0, 0, 0, 0x3C)) // ttl 60
        out.write(byteArrayOf(0, 16))
        // 2408:8256:2284:20a5:56d1:96ff:fed5:d317
        out.write(
            byteArrayOf(
                0x24, 0x08, 0x82.toByte(), 0x56, 0x22, 0x84.toByte(), 0x20, 0xA5.toByte(),
                0x56, 0xD1.toByte(), 0x96.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xD5.toByte(),
                0xD3.toByte(), 0x17,
            ),
        )
        return out.toByteArray()
    }
}
