package com.justnpcx.starveil.core.data.network

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the exit looks like from the outside: the address and how long it took. */
data class ExitInfo(
    val address: String,
    val rttMs: Long,
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
 * The endpoint is Cloudflare's trace: one plain GET, no API key, answering with
 * `ip=` plus a few lines of noise. The round trip shown is the whole request,
 * DNS and TLS included — it is not a ping, and the UI does not call it one.
 */
@Singleton
open class ExitProbe @Inject constructor() {

    /**
     * Open, not final: a test that builds a screen must not send a request to
     * a third party to draw a line of text.
     */
    open suspend fun probe(): ExitInfo? = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val connection = try {
            (URL(TRACE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
        } catch (e: Exception) {
            return@withContext null
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val address = exitAddressFromTrace(body) ?: return@withContext null
            ExitInfo(
                address = address,
                rttMs = (System.nanoTime() - startedAt) / 1_000_000,
                atEpochMs = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private companion object {
        const val TRACE_URL = "https://1.1.1.1/cdn-cgi/trace"
        const val TIMEOUT_MS = 5_000
    }
}

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
    ?.takeIf { it.isNotEmpty() }
