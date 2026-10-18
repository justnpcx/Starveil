package com.justnpcx.starveil.core.data.ruleset

import com.justnpcx.starveil.core.model.MainlandRuleSets
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * One rule-set file: where it comes from, and what it must hash to.
 *
 * The URLs are pinned to a commit, not a branch. A branch moves under the
 * app's feet, which makes "the file changed" indistinguishable from "the
 * project published an update" — and the one case this check exists for is
 * exactly the one that looks the same.
 */
data class PinnedRuleSet(
    val name: String,
    val sha256: String,
    val urls: List<String>,
)

/**
 * A rule set that could not be trusted.
 *
 * Carried out of the store rather than only logged: a preset that quietly
 * stops working is the failure mode this type exists to end.
 */
data class RuleSetFailure(
    val name: String,
    val reason: Reason,
) {
    enum class Reason {
        /** The bytes did not hash to the pinned value. */
        HASH_MISMATCH,

        /** Every mirror failed before there was anything to hash. */
        DOWNLOAD_FAILED,
    }
}

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

    /**
     * The path of the advertising-domain set, downloading it if missing.
     *
     * Separate from [ensure] because it stacks: it is wanted under every
     * preset, and a failure here must not take the mainland split down with it.
     */
    suspend fun ensureAds(): String?

    /**
     * The most recent rejection, or null when the last attempt was clean.
     *
     * A `StateFlow` rather than a callback so a screen that opens later still
     * sees it: the download happens at connect time, which is usually not when
     * the rules page is on screen.
     */
    val failure: StateFlow<RuleSetFailure?>
}

/**
 * Files on disk, verified against a hash baked into the app.
 *
 * Why a baked hash rather than a `.sha256sum` fetched alongside: a checksum
 * served by the same channel as the payload proves nothing about that channel.
 * It would catch a truncated download and nothing else. A hash pinned in the
 * app proves the bytes are the ones a developer saw when the version was cut,
 * and it moves only when the app does — which is the same cadence as the
 * pinned commit in the URL.
 */
class FileMainlandRuleSetStore(
    private val directory: File,
    private val download: suspend (String) -> ByteArray = ::httpGet,
    private val warn: (String) -> Unit = {},
    // Injectable so a test can pin the hash of its own fixture. The defaults
    // are the real artifacts; nothing in the app passes these.
    private val geositeCn: PinnedRuleSet = GEOSITE_CN,
    private val geoipCn: PinnedRuleSet = GEOIP_CN,
    private val ads: PinnedRuleSet = ADS,
) : MainlandRuleSetStore {

    private val _failure = MutableStateFlow<RuleSetFailure?>(null)
    override val failure: StateFlow<RuleSetFailure?> = _failure.asStateFlow()

    override suspend fun ensure(): MainlandRuleSets? {
        val geosite = obtain(geositeCn)
        val geoip = obtain(geoipCn)
        if (geosite == null || geoip == null) {
            // Both are needed. Half a preset would route domestic names direct
            // and then send their addresses abroad, which is worse than not
            // having it at all. The caller then builds a config without the
            // preset, whose `final` is the proxy: everything goes through the
            // tunnel. That is the fail-closed direction, and it is deliberate —
            // a missing rule set must not turn into "send everything direct".
            return null
        }
        return MainlandRuleSets(
            geositeCnPath = geosite.absolutePath,
            geoipCnPath = geoip.absolutePath,
        )
    }

    override suspend fun ensureAds(): String? = obtain(ads)?.absolutePath

    private suspend fun obtain(set: PinnedRuleSet): File? {
        val target = File(directory, set.name)
        if (target.isFile && target.length() > 0) {
            if (matches(target, set.sha256)) {
                _failure.value = null
                return target
            }
            // Present but wrong: written by an older build with a different
            // pin, or replaced. Either way it is not the file we vetted.
            warn("rule set ${set.name}: on disk but does not match its hash; discarding")
            target.delete()
        }

        var sawMismatch = false
        for (url in set.urls) {
            try {
                val bytes = download(url)
                if (bytes.isEmpty()) throw IOException("empty body")
                if (!sha256(bytes).equals(set.sha256, ignoreCase = true)) {
                    // Not written anywhere. A rejected body must never reach the
                    // path sing-box reads, not even briefly.
                    sawMismatch = true
                    warn("rule set ${set.name}: hash mismatch from ${url.substringBefore("://")}")
                    continue
                }
                directory.mkdirs()
                // Written aside and moved into place: a half-written file that
                // is then treated as present would be a corrupt rule set with
                // no way to notice.
                val partial = File(directory, "${set.name}.part")
                partial.writeBytes(bytes)
                if (!partial.renameTo(target)) {
                    partial.delete()
                    throw IOException("could not move into place")
                }
                _failure.value = null
                return target
            } catch (e: Exception) {
                // The URL is not logged in full: it is a CDN path today, but
                // the shape is the same as a subscription URL and that rule is
                // absolute.
                warn("rule set ${set.name}: ${e.javaClass.simpleName} from ${url.substringBefore("://")}")
            }
        }

        _failure.value = RuleSetFailure(
            name = set.name,
            reason = if (sawMismatch) {
                RuleSetFailure.Reason.HASH_MISMATCH
            } else {
                RuleSetFailure.Reason.DOWNLOAD_FAILED
            },
        )
        return null
    }

    private fun matches(file: File, expected: String): Boolean = runCatching {
        sha256(file.readBytes()).equals(expected, ignoreCase = true)
    }.getOrDefault(false)

    companion object {
        /**
         * Pinned to `SagerNet/sing-geosite@4f2dea9` (rule-set branch) and
         * `Loyalsoldier/geoip@9294c07` (release branch), both read on
         * 2026-10-18. Updating a file means updating its hash in the same
         * commit — that pairing is the whole point.
         */
        const val GEOSITE_COMMIT = "4f2dea911f1dbc3fd7516dbc2c82e03a0add71e6"
        const val GEOIP_COMMIT = "9294c07224a94ad3fe9d48eea160e4406d14fbde"

        /**
         * jsDelivr first: it is the one that is reachable from the mainland
         * without a proxy, which is the whole situation this preset is for.
         * The GitHub raw host is the fallback for everywhere else.
         */
        val GEOSITE_CN = PinnedRuleSet(
            name = "geosite-cn.srs",
            sha256 = "87f3206c4272d1b916beccf34cb30fadeae516264d0f422be518715af2e32980",
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/SagerNet/sing-geosite@$GEOSITE_COMMIT/geosite-cn.srs",
                "https://raw.githubusercontent.com/SagerNet/sing-geosite/$GEOSITE_COMMIT/geosite-cn.srs",
            ),
        )

        val ADS = PinnedRuleSet(
            name = "geosite-ads.srs",
            sha256 = "84e2ed79617a492cb1adaf28bf0c59a1a42af854ab935605ba84e0deba386c88",
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/SagerNet/sing-geosite@$GEOSITE_COMMIT/geosite-category-ads-all.srs",
                "https://raw.githubusercontent.com/SagerNet/sing-geosite/$GEOSITE_COMMIT/geosite-category-ads-all.srs",
            ),
        )

        val GEOIP_CN = PinnedRuleSet(
            name = "geoip-cn.srs",
            sha256 = "974b0f2ee8095a7e6f3d39c1a1a4a5b10159e023d4f1d8f88fa773333884f850",
            urls = listOf(
                "https://cdn.jsdelivr.net/gh/Loyalsoldier/geoip@$GEOIP_COMMIT/srs/cn.srs",
                "https://raw.githubusercontent.com/Loyalsoldier/geoip/$GEOIP_COMMIT/srs/cn.srs",
            ),
        )

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_BYTES = 32 * 1024 * 1024
        private const val USER_AGENT = "Starveil"

        internal fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }

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
