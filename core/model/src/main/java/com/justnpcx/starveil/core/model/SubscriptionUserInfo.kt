package com.justnpcx.starveil.core.model

/**
 * What a subscription says about itself in the `subscription-userinfo` header.
 *
 * The header is a de-facto standard (Clash, sing-box, v2rayN all read it):
 * `upload=1; download=2; total=3; expire=1700000000`, bytes and Unix seconds.
 * Every field is optional, because providers publish partial headers, and a
 * missing field must read as "not stated" rather than as zero — "0 of 0 used"
 * is a worse lie than "no quota shown".
 */
data class SubscriptionUserInfo(
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expireEpochSeconds: Long? = null,
) {
    /** Upload plus download, or null when the provider stated neither. */
    val usedBytes: Long?
        get() = if (uploadBytes == null && downloadBytes == null) {
            null
        } else {
            (uploadBytes ?: 0L) + (downloadBytes ?: 0L)
        }

    /**
     * Whole days until [expireEpochSeconds], rounded up; null when there is no
     * expiry or it has already passed (0 would read as "expires today").
     */
    fun daysLeft(nowEpochSeconds: Long): Long? {
        val expire = expireEpochSeconds ?: return null
        val seconds = expire - nowEpochSeconds
        if (seconds <= 0L) return null
        return (seconds + 86_399L) / 86_400L
    }
}

/**
 * Parses the header, or null when it says nothing usable.
 *
 * Tolerant on purpose: separators may be `;` or `,`, spaces are optional,
 * unknown keys are ignored, and a value that is not a positive number is
 * dropped rather than turned into 0. A malformed header is not an error —
 * the subscription itself is fine.
 */
fun parseSubscriptionUserInfo(header: String?): SubscriptionUserInfo? {
    if (header.isNullOrBlank()) return null
    val fields = header.split(";", ",")
        .mapNotNull { part ->
            val i = part.indexOf("=")
            if (i <= 0) return@mapNotNull null
            val key = part.substring(0, i).trim().lowercase()
            val raw = part.substring(i + 1).trim()
            val value = raw.toLongOrNull()?.takeIf { it > 0L }
            if (value == null) null else key to value
        }
        .toMap()
    if (fields.isEmpty()) return null
    val info = SubscriptionUserInfo(
        uploadBytes = fields["upload"],
        downloadBytes = fields["download"],
        totalBytes = fields["total"],
        expireEpochSeconds = fields["expire"],
    )
    return if (info.uploadBytes == null && info.downloadBytes == null &&
        info.totalBytes == null && info.expireEpochSeconds == null
    ) {
        null
    } else {
        info
    }
}
