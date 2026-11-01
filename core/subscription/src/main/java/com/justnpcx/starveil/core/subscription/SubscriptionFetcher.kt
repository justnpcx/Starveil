package com.justnpcx.starveil.core.subscription

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What one fetch produced: the body, and the headers this app reads. */
data class FetchedSubscription(
    val body: String,
    /** Raw `subscription-userinfo`, or null when the server did not send one. */
    val userInfoHeader: String? = null,
)

/** Fetches the raw body of a subscription URL. Throws on any failure. */
interface SubscriptionFetcher {
    suspend fun fetch(url: String): String

    /**
     * Same fetch, plus the headers worth keeping.
     *
     * Defaulted rather than required so the existing fakes stay honest: a fake
     * that only knows the body is a server that sends no quota, which is the
     * common case and not a special one.
     */
    suspend fun fetchWithMeta(url: String): FetchedSubscription =
        FetchedSubscription(fetch(url), null)
}

/**
 * Where the fetcher dials when a subscription has to go through the tunnel.
 *
 * Null is the default and the right answer for almost everyone: a subscription
 * URL is normally reachable from the device's own network. Some are not — a
 * panel hosted where the device cannot reach it, or a URL that is only
 * reachable through the node — and for those the fetch has to leave through the
 * kernel instead. Android excludes this app's own traffic from its own tunnel,
 * so "through the kernel" means the loopback SOCKS inbound the config carries
 * when the user asked for this.
 *
 * Implemented outside this module: the port depends on a preference and on the
 * kernel actually running, and neither belongs in a parser.
 */
fun interface SubscriptionProxy {
    /** The loopback SOCKS port, or null to fetch directly. */
    fun port(): Int?
}

/**
 * A non-2xx response.
 *
 * Typed rather than an `IOException("HTTP 403")` because the status is what
 * the UI should show, and recovering it by parsing a message string is how a
 * reason becomes a guess.
 */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * `HttpURLConnection` fetcher — no extra HTTP dependency for M2.
 *
 * The URL is never logged: a subscription URL frequently carries a token in
 * its path or query (README "安全红线").
 */
class HttpUrlConnectionFetcher(
    /** Null port = straight out, which is the default. */
    private val proxy: SubscriptionProxy = SubscriptionProxy { null },
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxBytes: Int = 4 * 1024 * 1024,
) : SubscriptionFetcher {

    override suspend fun fetch(url: String): String = fetchWithMeta(url).body

    override suspend fun fetchWithMeta(url: String): FetchedSubscription = withContext(Dispatchers.IO) {
        val route = proxy.port()?.let { port ->
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        }
        val connection = (
            if (route == null) URL(url).openConnection() else URL(url).openConnection(route)
            ) as HttpURLConnection
        connection.apply {
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
                throw HttpStatusException(code)
            }
            // Read before the body: after disconnect() the headers are gone.
            val userInfo = connection.getHeaderField(USER_INFO_HEADER)
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
            return@withContext FetchedSubscription(String(body, Charsets.UTF_8), userInfo)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val USER_AGENT = "Starveil/0.1"

        /** The de-facto quota header; see SubscriptionUserInfo. */
        const val USER_INFO_HEADER = "subscription-userinfo"
    }
}
