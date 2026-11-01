package com.justnpcx.starveil.core.data.ruleset

import com.justnpcx.starveil.core.model.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which snapshot of the upstream rule sets this build should use.
 *
 * [dateIso] is the commit's own timestamp, which is what the rules page shows:
 * a commit hash means nothing to a person asking "how old are these rules".
 */
data class RuleSetVersion(val commit: String, val dateIso: String) {

    /** The date as a person reads it, or the raw value when it is not ISO. */
    val date: String get() = dateIso.take(10)
}

/** Where the latest version comes from. */
fun interface RuleSetVersionSource {
    /** Null when it cannot be had — a check that cannot run is not an error. */
    suspend fun latest(): RuleSetVersion?
}

/**
 * The head of the upstream rule-set branch.
 *
 * Pinned URLs are built from this commit, so the files a check downloads are
 * the ones the branch pointed at when the check ran — not whatever the branch
 * has moved to by the time the second file is fetched.
 */
class GitHubRuleSetVersionSource(
    private val get: suspend (String) -> ByteArray = ::httpGetJson,
    private val warn: (String) -> Unit = {},
    /** The branch this source reports on; two repositories, two URLs. */
    private val url: String = DEFAULT_URL,
) : RuleSetVersionSource {

    override suspend fun latest(): RuleSetVersion? = try {
        val root = Json.parseObject(get(url).toString(Charsets.UTF_8))
        val commit = root["commit"] as? Map<*, *>
        val sha = (commit?.get("sha") as? String)?.takeIf { it.isNotBlank() }
        val inner = commit?.get("commit") as? Map<*, *>
        val committer = inner?.get("committer") as? Map<*, *>
        val date = (committer?.get("date") as? String)?.takeIf { it.isNotBlank() }
        if (sha != null && date != null) RuleSetVersion(sha, date) else null
    } catch (e: Exception) {
        // Silent on purpose: this runs in the background every few days, and a
        // version check that could not run is not news anyone needs.
        warn("rule set version: " + e.javaClass.simpleName)
        null
    }

    private companion object {
        const val DEFAULT_URL = "https://api.github.com/repos/SagerNet/sing-geosite/branches/rule-set"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 15_000

        suspend fun httpGetJson(url: String): ByteArray = withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Starveil")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP " + code)
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
    }
}
