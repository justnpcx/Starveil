package com.justnpcx.starveil.core.data.ruleset

import java.io.File
import java.io.IOException

/**
 * A directory of files that are only trusted when they hash to a value baked
 * into the app.
 *
 * Extracted from [FileMainlandRuleSetStore] when the rule market arrived: both
 * features download `.srs` files from a pinned commit, and both need the same
 * three rules — never write bytes that failed their hash, never trust a
 * half-written file, and never let a failed fetch throw. Two copies of that
 * logic would eventually disagree about one of them.
 */
internal class PinnedFileCache(
    private val directory: File,
    private val download: suspend (String) -> ByteArray,
    private val warn: (String) -> Unit,
) {

    sealed interface Result {
        data class Obtained(val file: File) : Result

        data class Failed(val reason: RuleSetFailure.Reason) : Result
    }

    /**
     * The file, from disk when it is already right and from the first mirror
     * that serves the pinned bytes otherwise.
     *
     * A file on disk with the wrong hash is deleted rather than trusted: it was
     * written by an older pin, or replaced. Either way it is not what this
     * build vetted.
     */
    suspend fun obtain(set: PinnedRuleSet): Result {
        val target = File(directory, set.name)
        if (target.isFile && target.length() > 0) {
            if (matches(target, set.sha256)) return Result.Obtained(target)
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
                return Result.Obtained(target)
            } catch (e: Exception) {
                // The URL is not logged in full: it is a CDN path today, but
                // the shape is the same as a subscription URL and that rule is
                // absolute.
                warn("rule set ${set.name}: ${e.javaClass.simpleName} from ${url.substringBefore("://")}")
            }
        }

        return Result.Failed(
            if (sawMismatch) {
                RuleSetFailure.Reason.HASH_MISMATCH
            } else {
                RuleSetFailure.Reason.DOWNLOAD_FAILED
            },
        )
    }

    private fun matches(file: File, expected: String): Boolean = runCatching {
        sha256(file.readBytes()).equals(expected, ignoreCase = true)
    }.getOrDefault(false)

    companion object {
        fun sha256(bytes: ByteArray): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
