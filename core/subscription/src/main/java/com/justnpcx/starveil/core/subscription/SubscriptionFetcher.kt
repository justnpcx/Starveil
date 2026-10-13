package com.justnpcx.starveil.core.subscription

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Fetches the raw body of a subscription URL. Throws on any failure. */
interface SubscriptionFetcher {
    suspend fun fetch(url: String): String
}

/**
 * `HttpURLConnection` fetcher — no extra HTTP dependency for M2.
 *
 * The URL is never logged: a subscription URL frequently carries a token in
 * its path or query (README "安全红线").
 */
class HttpUrlConnectionFetcher(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxBytes: Int = 4 * 1024 * 1024,
) : SubscriptionFetcher {

    override suspend fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "*/*")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            // Not InputStream.readNBytes: that is API 33, and minSdk is 29.
            val body = connection.inputStream.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (total < maxBytes) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    val take = minOf(read, maxBytes - total)
                    buffer.write(chunk, 0, take)
                    total += take
                }
                buffer.toByteArray()
            }
            return String(body, Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val USER_AGENT = "Starveil/0.1"
    }
}
