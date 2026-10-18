package com.justnpcx.starveil.core.data.update

import android.os.Build
import com.justnpcx.starveil.core.model.json.Json
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A release that can be installed, and the facts needed to check it. */
data class UpdateInfo(
    /** Tag with the leading `v` removed, e.g. `0.8.0`. */
    val version: String,
    val notes: String,
    val apkUrl: String,
    /**
     * SHA-256 of the APK, as GitHub publishes it (`assets[].digest`).
     * Null on a release uploaded before GitHub started returning digests.
     */
    val sha256: String?,
    val sizeBytes: Long,
)

sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

/**
 * Reads the latest release from GitHub.
 *
 * The repository is private today, so this answers [UpdateCheck.Failed] until
 * M8 makes it public — a wrong answer would be worse than an honest "could not
 * check", and the release page is still one tap away.
 *
 * `fetch` is injected so the parsing and the version comparison can be tested
 * against a recorded response. That is the part with rules in it; the HTTP call
 * is not.
 */
class UpdateChecker internal constructor(
    private val fetch: suspend (String) -> String,
) {

    @Inject
    constructor() : this(::httpGet)

    suspend fun check(currentVersion: String, repo: String = REPO): UpdateCheck {
        val body = try {
            fetch("https://api.github.com/repos/$repo/releases/latest")
        } catch (e: Exception) {
            return UpdateCheck.Failed(e.message ?: e::class.java.simpleName)
        }
        return parse(body, currentVersion, deviceAbi())
    }

    /**
     * [abi] is the device's primary ABI, or null when it is not one of the
     * three this project ships. A release carries one APK per ABI; installing
     * the arm64 package on a 32-bit phone is not an update, it is a brick.
     */
    internal fun parse(body: String, currentVersion: String, abi: String?): UpdateCheck {
        val release = runCatching { Json.parseObject(body) }.getOrNull()
            ?: return UpdateCheck.Failed("unreadable release")
        if (release["draft"] == true) return UpdateCheck.UpToDate

        val tag = release["tag_name"] as? String
            ?: return UpdateCheck.Failed("release has no tag")
        val version = tag.removePrefix("v")
        if (!isNewer(version, currentVersion)) return UpdateCheck.UpToDate

        val apks = (release["assets"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            ?.filter { (it["name"] as? String)?.endsWith(".apk") == true }
            .orEmpty()

        // The matching asset first. A single APK is the older, universal
        // layout and is safe to take when nothing matches; several with no
        // match means this device has no package in this release, and taking
        // one anyway is exactly the wrong-ABI install this check exists for.
        val asset = apks.firstOrNull { candidate ->
            abi != null && (candidate["name"] as? String)?.contains(abi) == true
        } ?: apks.singleOrNull()
            ?: return UpdateCheck.Failed(
                if (abi == null) {
                    "release has no APK for this device"
                } else {
                    "release has no APK for $abi"
                },
            )

        val url = asset["browser_download_url"] as? String
            ?: return UpdateCheck.Failed("APK has no download URL")
        // `sha256:abc…`; the algorithm prefix is part of the format.
        val digest = (asset["digest"] as? String)
            ?.substringAfter("sha256:", "")
            ?.takeIf { it.isNotBlank() }
        val size = (asset["size"] as? Number)?.toLong() ?: 0L

        return UpdateCheck.Available(
            UpdateInfo(
                version = version,
                notes = (release["body"] as? String).orEmpty(),
                apkUrl = url,
                sha256 = digest,
                sizeBytes = size,
            ),
        )
    }

    private fun deviceAbi(): String? =
        Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS }

    private companion object {
        /** Public in M8; until then this answers "could not check". */
        const val REPO = "justnpcx/Starveil"

        /** The three libbox ships; x86 32-bit is deliberately absent. */
        val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }
}

/**
 * True when [candidate] is a later release than [current].
 *
 * Numeric components only, compared left to right; a missing component counts
 * as zero. A pre-release suffix (`0.8.0-beta.1`) is older than the release it
 * precedes, which is what GitHub's `latest` endpoint already assumes by
 * skipping pre-releases — this only has to not disagree with it.
 */
internal fun isNewer(candidate: String, current: String): Boolean {
    val a = versionParts(candidate)
    val b = versionParts(current)
    for (index in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(index) { 0 }
        val right = b.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    // Same numbers: a bare release beats a pre-release of it.
    val candidatePre = candidate.contains('-')
    val currentPre = current.contains('-')
    return currentPre && !candidatePre
}

private fun versionParts(version: String): List<Int> =
    version.removePrefix("v")
        .substringBefore('-')
        .split('.')
        .mapNotNull { it.toIntOrNull() }

private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/vnd.github+json")
        // GitHub rejects requests without a User-Agent.
        setRequestProperty("User-Agent", "Starveil-UpdateChecker")
    }
    try {
        val code = connection.responseCode
        if (code != 200) {
            throw IllegalStateException(
                when (code) {
                    404 -> "no published release"
                    403 -> "GitHub rate limit"
                    else -> "HTTP $code"
                },
            )
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
