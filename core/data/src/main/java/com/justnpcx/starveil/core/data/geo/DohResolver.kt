package com.justnpcx.starveil.core.data.geo

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Resolves a hostname over DNS-over-HTTPS (RFC 8484).
 *
 * Why: a plain `InetAddress.getByName` puts the node's hostname on the wire in
 * cleartext, to whichever resolver the network hands out. The set of servers a
 * user connects to is the interesting part of a proxy client, and broadcasting
 * it to the local network is exactly what this app exists to avoid.
 *
 * The message is built and parsed here rather than with a DNS library: an A
 * query is twelve bytes of header, a name, and four bytes of question, and a
 * library for that would be a dependency with its own update cadence for
 * something this file does in eighty lines.
 *
 * Aliyun first, DNSPod second: both answered in ~120–150 ms from the test
 * network, Aliyun consistently faster, and both are reachable from the
 * mainland without a proxy — which is the network this has to work on.
 */
class DohResolver(
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
    /** Injected for tests: (url, accept) -> response body. */
    private val fetch: suspend (String, String) -> ByteArray = ::httpGet,
    /** The old path, used when every endpoint fails. */
    private val fallback: suspend (String) -> String? = ::systemResolve,
    // No-op by default so a JVM test does not need android.util.Log; the DI
    // provider passes the logcat one.
    private val warn: (String) -> Unit = {},
) {

    // Eight at once: a subscription can hold hundreds of nodes, and the point
    // of resolving them together is to be quicker than one at a time, not to
    // open hundreds of sockets.
    private val permits = Semaphore(MAX_CONCURRENT)

    /**
     * The first IPv4 address for [host], or null.
     *
     * Falls back to the system resolver when every DoH endpoint fails — a
     * grouping feature that stops working because one resolver is down is
     * worse than a query that is briefly in cleartext. The fallback is logged.
     */
    suspend fun resolve(host: String): String? {
        if (host.isBlank()) return null
        // A literal address needs no lookup; asking would be a round trip for
        // something already known.
        if (isIpv4Literal(host)) return host

        val query = DnsMessage.query(host)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(query)
        for (endpoint in endpoints) {
            val url = "$endpoint?dns=$encoded"
            val startedAt = System.nanoTime()
            val body = try {
                permits.withPermit {
                    fetch(url, DNS_MESSAGE)
                }
            } catch (e: Exception) {
                warn("DoH ${endpoint.substringAfter("://")} failed: ${e.javaClass.simpleName}")
                continue
            }
            DnsMessage.firstAddress(body)?.let {
                // No hostname here: the log ring is readable from the UI, and
                // which servers someone connects to is the thing this class
                // exists to keep off the wire.
                warn("DoH answered in ${(System.nanoTime() - startedAt) / 1_000_000} ms")
                return it
            }
            warn("DoH ${endpoint.substringAfter("://")} answered without an A record")
        }

        warn("DoH unavailable; falling back to the system resolver for a node host")
        return fallback(host)
    }

    private companion object {
        const val TAG = "StarveilDoh"
        const val DNS_MESSAGE = "application/dns-message"
        const val MAX_CONCURRENT = 8
        const val TIMEOUT_MS = 3_000

        val DEFAULT_ENDPOINTS = listOf(
            "https://223.5.5.5/dns-query",
            "https://120.53.53.53/dns-query",
        )

        fun isIpv4Literal(host: String): Boolean =
            host.count { it == '.' } == 3 && host.all { it.isDigit() || it == '.' }

        suspend fun systemResolve(host: String): String? = withContext(Dispatchers.IO) {
            runCatching { java.net.InetAddress.getByName(host).hostAddress }.getOrNull()
        }

        suspend fun httpGet(url: String, accept: String): ByteArray = withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "Starveil")
            }
            try {
                if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
    }
}

/**
 * The smallest DNS wire format that answers "what is this host's IPv4".
 *
 * Only A records are asked for and only A records are read: the GeoIP database
 * this feeds is IPv4, and an AAAA answer would be a lookup that always misses.
 */
internal object DnsMessage {

    /** A query for [host]'s A record, with a fixed transaction id. */
    fun query(host: String, id: Int = 0): ByteArray {
        val name = host.trimEnd('.').split('.').filter { it.isNotEmpty() }
        require(name.isNotEmpty()) { "empty host" }
        require(name.all { it.length in 1..63 }) { "label too long" }

        val out = java.io.ByteArrayOutputStream(32)
        // Header: id, flags (recursion desired), one question, no records.
        out.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in name) {
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0) // root label
        out.write(byteArrayOf(0, 1)) // QTYPE A
        out.write(byteArrayOf(0, 1)) // QCLASS IN
        return out.toByteArray()
    }

    /**
     * The first A record's address, or null.
     *
     * Null for anything that is not a successful answer — a refused query, a
     * truncated message, a response whose only records are AAAA. The caller
     * treats all of those the same way: try the next endpoint.
     */
    fun firstAddress(response: ByteArray): String? {
        if (response.size < 12) return null
        val flags = ((response[2].toInt() and 0xFF) shl 8) or (response[3].toInt() and 0xFF)
        if (flags and 0x8000 == 0) return null // not a response
        if (flags and 0x000F != 0) return null // RCODE != 0
        val questions = readU16(response, 4)
        val answers = readU16(response, 6)

        var offset = 12
        repeat(questions) {
            offset = skipName(response, offset) ?: return null
            offset += 4 // qtype + qclass
            if (offset > response.size) return null
        }

        repeat(answers) {
            offset = skipName(response, offset) ?: return null
            if (offset + 10 > response.size) return null
            val type = readU16(response, offset)
            val length = readU16(response, offset + 8)
            val dataStart = offset + 10
            if (dataStart + length > response.size) return null
            if (type == TYPE_A && length == 4) {
                return "${response[dataStart].toInt() and 0xFF}." +
                    "${response[dataStart + 1].toInt() and 0xFF}." +
                    "${response[dataStart + 2].toInt() and 0xFF}." +
                    "${response[dataStart + 3].toInt() and 0xFF}"
            }
            offset = dataStart + length
        }
        return null
    }

    private const val TYPE_A = 1

    private fun readU16(data: ByteArray, at: Int): Int =
        ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

    /** Skips a possibly-compressed name, returning the offset after it. */
    private fun skipName(data: ByteArray, start: Int): Int? {
        var offset = start
        while (true) {
            if (offset >= data.size) return null
            val length = data[offset].toInt() and 0xFF
            when {
                length == 0 -> return offset + 1
                // Compression pointer: two bytes and the name ends here.
                length and 0xC0 == 0xC0 -> return offset + 2
                else -> offset += 1 + length
            }
        }
    }
}
