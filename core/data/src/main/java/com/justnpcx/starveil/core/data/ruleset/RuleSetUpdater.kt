package com.justnpcx.starveil.core.data.ruleset

import java.io.File

/**
 * Where one rule set comes from, and how to address it at a given commit.
 *
 * The URLs are built from the commit a check discovered rather than baked in,
 * so a batch is fetched from one snapshot even if the branch moves while the
 * second file is in flight.
 */
data class RuleSetFeed(
    /** File name on disk. */
    val name: String,
    /** Repository, in owner/name form, for the URL template. */
    val repo: String,
    /** Path inside the repository. */
    val path: String,
    /** Which branch head is the "latest" for this feed. */
    val branch: String,
    /** True for the two files that ship in the APK and are always fetched. */
    val core: Boolean = true,
) {
    /** The two mirrors, in the order the store already prefers. */
    fun urls(commit: String): List<String> = listOf(
        "https://cdn.jsdelivr.net/gh/" + repo + "@" + commit + "/" + path,
        "https://raw.githubusercontent.com/" + repo + "/" + commit + "/" + path,
    )
}

/**
 * One check of the upstream, end to end: discover, download, install.
 *
 * Nothing here writes a file directly — [RuleSetInstaller] owns that, and it
 * only moves a batch that passed its checks. This class decides what to fetch
 * and what the result means.
 */
class RuleSetUpdater(
    private val directory: File,
    private val installer: RuleSetInstaller,
    private val versionOf: suspend (RuleSetFeed) -> RuleSetVersion?,
    private val download: suspend (String) -> ByteArray,
    private val feeds: List<RuleSetFeed> = CORE_FEEDS,
    private val warn: (String) -> Unit = {},
) {

    sealed interface Result {
        /** The commit on disk already is the head. */
        data class UpToDate(val version: RuleSetVersion) : Result

        data class Updated(val version: RuleSetVersion, val files: Int) : Result

        /** Nothing changed on disk; [reason] is for the log, not the screen. */
        data class Failed(val reason: String) : Result
    }

    /** The generation on disk, or null before the first successful install. */
    fun installedVersion(): RuleSetVersion? = installer.readManifest()?.version

    suspend fun check(extra: List<RuleSetFeed> = emptyList()): Result {
        val all = feeds + extra
        val head = versionOf(all.first { it.core }) ?: return Result.Failed("no version")
        val onDisk = installer.readManifest()
        if (onDisk != null && onDisk.version.commit == head.commit) {
            return Result.UpToDate(head)
        }

        val bodies = HashMap<String, ByteArray>()
        for (feed in all) {
            val version = if (feed.branch == all.first().branch) head else versionOf(feed)
            if (version == null) return Result.Failed(feed.name + ": no version")
            val bytes = fetch(feed, version.commit)
                ?: return Result.Failed(feed.name + ": download failed")
            bodies[feed.name] = bytes
        }

        return when (val outcome = installer.install(head, bodies)) {
            is RuleSetInstaller.Outcome.Installed -> Result.Updated(head, outcome.files.size)
            is RuleSetInstaller.Outcome.Rejected -> Result.Failed(outcome.reason)
        }
    }

    /** First mirror that answers; null when none does. */
    private suspend fun fetch(feed: RuleSetFeed, commit: String): ByteArray? {
        for (url in feed.urls(commit)) {
            val bytes = runCatching { download(url) }.getOrNull() ?: continue
            if (bytes.isNotEmpty()) return bytes
        }
        warn("rule set " + feed.name + ": no mirror served it")
        return null
    }

    companion object {
        const val GEOSITE_REPO = "SagerNet/sing-geosite"
        const val GEOIP_REPO = "Loyalsoldier/geoip"

        /**
         * The three files a mainland preset cannot work without.
         *
         * Two repositories: the geosite files and the address list are
         * published separately, so each has its own head. The version shown to
         * a person is the geosite one — that is the file that decides routing.
         */
        val CORE_FEEDS = listOf(
            RuleSetFeed("geosite-cn.srs", GEOSITE_REPO, "geosite-cn.srs", "rule-set"),
            RuleSetFeed("geosite-ads.srs", GEOSITE_REPO, "geosite-category-ads-all.srs", "rule-set"),
            RuleSetFeed("geoip-cn.srs", GEOIP_REPO, "srs/cn.srs", "release"),
        )

        /**
         * Only fetched when the mainland preset is on: it exists to correct
         * that preset, and nothing else reads it.
         */
        val GOOGLE_FEED = RuleSetFeed(
            "geosite-google.srs",
            GEOSITE_REPO,
            "geosite-google.srs",
            "rule-set",
            core = false,
        )
    }
}
