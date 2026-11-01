package com.justnpcx.starveil.core.data.ruleset

import com.justnpcx.starveil.core.model.json.Json
import com.justnpcx.starveil.core.model.ruleset.Srs
import java.io.File

/** What is on disk now: which upstream commit, and the hash of each file. */
data class RuleSetManifest(
    val version: RuleSetVersion,
    /** File name to the SHA-256 of the bytes that were installed. */
    val hashes: Map<String, String> = emptyMap(),
)

/**
 * Installs a new generation of rule sets, or none at all.
 *
 * The unit of success is the batch, not the file: half a new generation is a
 * preset that routes by two different snapshots, which is worse than an old
 * one that routes consistently. So every file is checked first, and only a
 * batch that passes every check is moved into place.
 *
 * The previous generation is kept beside the current one as name.prev — one
 * generation, not a history. It is what a rollback reads, and it is the only
 * thing that makes a bad batch recoverable without a download.
 */
internal class RuleSetInstaller(
    private val directory: File,
    private val warn: (String) -> Unit = {},
    /** How much of the current set a new one must still cover. */
    private val minimumFraction: Double = 0.5,
) {

    sealed interface Outcome {
        data class Installed(val files: List<String>) : Outcome

        /** [reason] is a stable phrase for the log; nothing user-facing. */
        data class Rejected(val reason: String) : Outcome
    }

    fun install(version: RuleSetVersion, bodies: Map<String, ByteArray>): Outcome {
        if (bodies.isEmpty()) return Outcome.Rejected("nothing to install")

        // Check every file before writing any of them.
        for ((name, bytes) in bodies) {
            val parsed = runCatching { Srs.read(bytes) }.getOrNull()
                ?: return Outcome.Rejected(name + ": not a readable rule set")
            // Entries, not rules: one upstream rule commonly carries a whole
            // list of domains, so a rule count barely moves when a set is
            // truncated. The number that has to hold up is how many names and
            // ranges the file actually covers.
            val count = entryCount(parsed)
            if (count <= 0) return Outcome.Rejected(name + ": parsed to no rules")
            val current = currentRuleCount(name)
            if (current > 0 && count < (current * minimumFraction).toInt()) {
                return Outcome.Rejected(
                    name + ": " + count + " rules against " + current + " in use",
                )
            }
        }

        directory.mkdirs()
        val installed = mutableListOf<String>()
        for ((name, bytes) in bodies) {
            val target = File(directory, name)
            val next = File(directory, name + ".next")
            val previous = File(directory, name + ".prev")
            next.writeBytes(bytes)
            // One generation back, replaced rather than accumulated.
            if (target.isFile) {
                previous.delete()
                if (!target.renameTo(previous)) {
                    next.delete()
                    warn("rule set " + name + ": could not set the previous generation aside")
                    return Outcome.Rejected(name + ": could not rotate")
                }
            }
            if (!next.renameTo(target)) {
                next.delete()
                // Put the old one back before reporting, or the preset loses a
                // file it had a moment ago.
                if (previous.isFile) previous.renameTo(target)
                warn("rule set " + name + ": could not move the new generation into place")
                return Outcome.Rejected(name + ": could not install")
            }
            installed += name
        }

        writeManifest(
            RuleSetManifest(
                version = version,
                hashes = bodies.mapValues { (_, bytes) -> FileMainlandRuleSetStore.sha256(bytes) },
            ),
        )
        warn("rule sets: installed " + installed.size + " file(s) from " + version.date)
        return Outcome.Installed(installed)
    }

    /**
     * The previous generation of [name], when it is still readable.
     *
     * Used when the current file is missing or unreadable: an old rule set that
     * works beats no preset at all.
     */
    fun rollback(name: String): File? {
        val previous = File(directory, name + ".prev")
        if (!previous.isFile || previous.length() == 0L) return null
        val readable = runCatching { Srs.read(previous.readBytes()).rules.isNotEmpty() }
            .getOrDefault(false)
        if (!readable) return null
        val target = File(directory, name)
        target.delete()
        if (!previous.renameTo(target)) return null
        warn("rule set " + name + ": rolled back to the previous generation")
        return target
    }

    fun readManifest(): RuleSetManifest? {
        val file = File(directory, MANIFEST)
        if (!file.isFile) return null
        return runCatching {
            val root = Json.parseObject(file.readText())
            val commit = (root["commit"] as? String) ?: return null
            val date = (root["date"] as? String) ?: return null
            val hashes = (root["hashes"] as? Map<*, *>)
                ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
                ?.toMap()
                .orEmpty()
            RuleSetManifest(RuleSetVersion(commit, date), hashes)
        }.getOrNull()
    }

    private fun writeManifest(manifest: RuleSetManifest) {
        val json = linkedMapOf<String, Any?>(
            "commit" to manifest.version.commit,
            "date" to manifest.version.dateIso,
            "hashes" to manifest.hashes,
        )
        File(directory, MANIFEST).writeText(Json.write(json))
    }

    /** Entry count of the file in use, or 0 when there is none to compare. */
    private fun currentRuleCount(name: String): Int {
        val file = File(directory, name)
        if (!file.isFile || file.length() == 0L) return 0
        return runCatching { entryCount(Srs.read(file.readBytes())) }.getOrDefault(0)
    }

    /** How many names and ranges the set covers. */
    private fun entryCount(set: com.justnpcx.starveil.core.model.ruleset.SrsRuleSet): Int =
        set.rules.sumOf { rule ->
            rule.exactDomains.size + rule.domainSuffixes.size +
                rule.domainKeywords.size + rule.ipRanges.size
        }

    private companion object {
        const val MANIFEST = "version.json"
    }
}
