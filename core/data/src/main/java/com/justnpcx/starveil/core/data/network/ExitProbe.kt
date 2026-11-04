package com.justnpcx.starveil.core.data.network

import android.util.Log
import com.justnpcx.starveil.core.data.log.AppLogSink
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** What the exit looks like from the outside: the address and how long it took. */
data class ExitInfo(
    val address: String,
    val rttMs: Long,
    val atEpochMs: Long,
)

/** The last endpoint that failed, and why. Read by the diagnostics page. */
data class ExitProbeFailure(
    val endpoint: String,
    val reason: String,
    val atEpochMs: Long,
)

/**
 * Asks a third party what address it sees us coming from.
 *
 * The point is not the address itself — it is whether the user can tell that
 * the tunnel is doing anything at all. A proxy that silently fell back to the
 * local connection looks exactly like one that works, until this number turns
 * out to be the home address.
 *
 * **More than one endpoint, tried in order.** A single endpoint is a single
 * point of failure, and 0.9.20 proved it on device: through a Cloudflare
 * front, `https://1.1.1.1/cdn-cgi/trace` completes its handshake but the edge
 * answers with a certificate that does not cover the IP literal, so
 * `HttpURLConnection` throws and the card never appears. The first endpoint is
 * therefore the same trace behind a hostname — a name the edge's certificate
 * does cover — and the IP literal stays as the second try because on a direct
 * route it is the shortest path. The third is outside Cloudflare entirely, so
 * one operator's outage cannot hide the exit.
 *
 * Every failure is recorded: a WARN in the log ring (so the Logs screen and the
 * diagnostics export can say why the card is missing) and [lastFailure] for the
 * diagnostics page. Silently swallowing the exception is what made the original
 * bug invisible for a whole release.
 *
 * The round trip shown is the whole request, DNS and TLS included — it is not a
 * ping, and the UI does not call it one.
 */
@Singleton
open class ExitProbe @Inject constructor(
    private val appLog: AppLogSink,
) {

    private val _lastFailure = MutableStateFlow<ExitProbeFailure?>(null)

    /** The most recent endpoint failure, or null after a successful probe. */
    val lastFailure: StateFlow<ExitProbeFailure?> = _lastFailure.asStateFlow()

    /**
     * The first endpoint that answers with a usable address, or null when the
     * whole chain failed.
     */
    open suspend fun probe(): ExitInfo? = withContext(Dispatchers.IO) {
        for (endpoint in ENDPOINTS) {
            val startedAt = System.nanoTime()
            try {
                val body = fetch(endpoint.url)
                val address = endpoint.parse(body)
                if (address == null) {
                    recordFailure(endpoint.url, "unparsable body")
                    continue
                }
                _lastFailure.value = null
                return@withContext ExitInfo(
                    address = address,
                    rttMs = (System.nanoTime() - startedAt) / 1_000_000,
                    atEpochMs = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                recordFailure(endpoint.url, describe(e))
            }
        }
        null
    }

    /**
     * One GET. Open so the fallback order can be tested without a network:
     * the interesting cases are a handshake failure on the first endpoint and
     * a success on the second, and no device produces those on demand.
     */
    protected open suspend fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw ProbeHttpException(code)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun recordFailure(endpoint: String, reason: String) {
        _lastFailure.value = ExitProbeFailure(endpoint, reason, System.currentTimeMillis())
        val message = "exit probe failed: $endpoint: $reason"
        Log.w(TAG, message)
        appLog.warn(TAG, message)
    }

    private fun describe(e: Exception): String =
        if (e is ProbeHttpException) "HTTP ${e.code}" else e.javaClass.simpleName

    private data class Endpoint(val url: String, val parse: (String) -> String?)

    private companion object {
        const val TAG = "exit"

        /**
         * A hostname first: the certificate covers the name, so a Cloudflare
         * front that mangles the IP literal's certificate still works here.
         */
        const val CLOUDFLARE_TRACE = "https://www.cloudflare.com/cdn-cgi/trace"

        /** The IP literal, kept for the direct route where it is shortest. */
        const val CLOUDFLARE_TRACE_LITERAL = "https://1.1.1.1/cdn-cgi/trace"

        /**
         * Outside Cloudflare, no API key, plain-text answer. ipify has served
         * this shape since 2013 and answers with the address and nothing else,
         * which is the smallest surface to parse.
         */
        const val IPIFY = "https://api.ipify.org"

        const val TIMEOUT_MS = 5_000

        val ENDPOINTS = listOf(
            Endpoint(CLOUDFLARE_TRACE, ::exitAddressFromTrace),
            Endpoint(CLOUDFLARE_TRACE_LITERAL, ::exitAddressFromTrace),
            Endpoint(IPIFY, ::exitAddressFromPlain),
        )
    }
}

/** A non-2xx answer, kept apart from transport errors so the log can say which. */
private class ProbeHttpException(val code: Int) : Exception("HTTP $code")

/**
 * The `ip=` line of a Cloudflare trace body, or null.
 *
 * Kept out of [ExitProbe] so the parsing can be tested without a network, and
 * kept strict: a body with no `ip=` line is "could not tell", never an empty
 * address shown as if it were one.
 */
fun exitAddressFromTrace(body: String): String? = body.lineSequence()
    .firstOrNull { it.startsWith("ip=") }
    ?.removePrefix("ip=")
    ?.trim()
    ?.takeIf { isValidAddress(it) }

/**
 * A body that is only the address, which is what ipify answers.
 *
 * Whitespace and a trailing newline are all it may add; anything else is not an
 * address and is rejected rather than trimmed into one.
 */
fun exitAddressFromPlain(body: String): String? = body.trim().takeIf { isValidAddress(it) }

/**
 * True only for an IPv4 or IPv6 literal.
 *
 * The character gate matters: [InetAddress.getByName] resolves host names, so a
 * body saying `example.com` would otherwise become an "address". Restricting
 * the set to hex digits, dots and colons means the call can only ever see a
 * literal, and the parse itself rejects `999.1.1.1` and friends.
 */
internal fun isValidAddress(value: String): Boolean {
    if (value.isEmpty() || value.length > 45) return false
    val allowed = value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '.' || it == ':' }
    if (!allowed) return false
    val looksIpv4 = ':' !in value && value.count { it == '.' } == 3
    if (!looksIpv4 && ':' !in value) return false
    return runCatching { InetAddress.getByName(value) }.isSuccess
}
