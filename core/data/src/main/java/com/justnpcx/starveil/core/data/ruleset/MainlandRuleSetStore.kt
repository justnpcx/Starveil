package com.justnpcx.starveil.core.data.ruleset

import com.justnpcx.starveil.core.model.MainlandRuleSets
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The two rule sets the mainland preset routes by, on disk.
 *
 * The app owns this download rather than pointing sing-box at a URL, because
 * sing-box cannot be told to tolerate a failed fetch: the router starts rule
 * sets with `FastFail` (route/router.go) and a remote set fetches inside
 * `StartContext`, returning the error when the first fetch fails. Offline, or
 * behind a blocked CDN, that fails the whole start and the tunnel never comes
 * up. Here a failure is just `null`, and the preset is left out of the config.
 */
interface MainlandRuleSetStore {
    /**
     * The paths of both files, downloading whichever is missing. Null when they
     * cannot be had — never an exception, because "no preset" is a working
     * state and the caller has nothing useful to do with a throw.
     */
    suspend fun ensure(): MainlandRuleSets?
}

class FileMainlandRuleSetStore(
    private val directory: File,
    private val download: suspend (String) -> ByteArray = ::httpGet,
    private val warn: (String) -> Unit = {},
) : MainlandRuleSetStore {

    override suspend fun ensure(): MainlandRuleSets? {
        val geosite = obtain("geosite-cn.srs", GEOSITE_CN_URLS)
        val geoip = obtain("geoip-cn.srs", GEOIP_CN_URLS)
        if (geosite == null || geoip == null) {
            // Both are needed. Half a preset would route domestic names direct
            // and then send their addresses abroad, which is worse than not
            // having it at all.
            return null
        }
        return MainlandRuleSets(
            geositeCnPath = geosite.absolutePath,
            geoipCnPath = geoip.absolutePath,
        )
    }

    private suspend fun obtain(name: String, urls: List<String>): File? {
        val target = File(directory, name)
        if (target.isFile && target.length() > 0) return target

        for (url in urls) {
            try {
                val bytes = download(url)
                if (bytes.isEmpty()) throw IOException("empty body")
                directory.mkdirs()
                // Written aside and moved into place: a half-written file that
                // is then treated as present would be a corrupt rule set with
                // no way to notice.
                val partial = File(directory, "$name.part")
                partial.writeBytes(bytes)
                if (!partial.renameTo(target)) {
                    partial.delete()
                    throw IOException("could not move into place")
                }
                return target
            } catch (e: Exception) {
                // The URL is not logged: it is a CDN path today, but the shape
                // is the same as a subscription URL and that rule is absolute.
                warn("rule set $name: ${e.javaClass.simpleName} from ${url.substringBefore("://")}")
            }
        }
        return null
    }

    companion object {
        /**
         * jsDelivr first: it is the one that is reachable from the mainland
         * without a proxy, which is the whole situation this preset is for.
         * The GitHub raw host is the fallback for everywhere else.
         */
        val GEOSITE_CN_URLS = listOf(
            "https://cdn.jsdelivr.net/gh/SagerNet/sing-geosite@rule-set/geosite-cn.srs",
            "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs",
        )
        val GEOIP_CN_URLS = listOf(
            "https://cdn.jsdelivr.net/gh/Loyalsoldier/geoip@release/srs/cn.srs",
            "https://raw.githubusercontent.com/Loyalsoldier/geoip/release/srs/cn.srs",
        )

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_BYTES = 32 * 1024 * 1024
        private const val USER_AGENT = "Starveil"

        suspend fun httpGet(url: String): ByteArray = withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "*/*")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                connection.inputStream.use { it.readBytesCapped(MAX_BYTES) }
            } finally {
                connection.disconnect()
            }
        }

        private fun java.io.InputStream.readBytesCapped(max: Int): ByteArray {
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val read = read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                if (buffer.size() > max) throw IOException("larger than $max bytes")
            }
            return buffer.toByteArray()
        }
    }
}
