package com.justnpcx.starveil.core.model

/**
 * Local copies of the rule sets the mainland preset routes by.
 *
 * Paths rather than URLs, and that is the whole point: sing-box fetches a
 * *remote* rule set inside `start()` and the router runs those fetches with
 * `FastFail`, so a first fetch that fails — offline, blocked, CDN down — fails
 * the whole start and the tunnel never comes up. The app downloads the files
 * instead, and this being non-null is the statement that they are on disk.
 *
 * Null everywhere it is accepted means the preset is not available, and the
 * config is emitted exactly as it was before the preset existed.
 */
data class MainlandRuleSets(
    val geositeCnPath: String,
    val geoipCnPath: String,
    /**
     * Domains that must be proxied even though geosite-cn lists them.
     *
     * The Google download hosts are in the upstream direct list — Play fetches
     * from googlevideo hosts that geosite-cn matches — so a mainland preset
     * sends them direct and the download stalls at 0%. Null when the file is
     * not on disk, and then the exception simply is not emitted.
     */
    val googlePath: String? = null,
)
