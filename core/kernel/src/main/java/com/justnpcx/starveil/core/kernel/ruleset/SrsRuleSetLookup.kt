package com.justnpcx.starveil.core.kernel.ruleset

import com.justnpcx.starveil.core.model.ruleset.SrsRule
import com.justnpcx.starveil.core.model.ruleset.SrsRuleSet

/**
 * A [com.justnpcx.starveil.core.kernel.config.RuleSetLookup] over parsed
 * `.srs` files, keyed by the tag the generated config uses (`ads`,
 * `geosite-cn`, `geoip-cn`, `pack-…`).
 *
 * The sets are passed in rather than loaded here: which files exist is the
 * caller's business (the store already downloads and hashes them), and a test
 * should not need a filesystem to ask a question about a domain.
 */
class SrsRuleSetLookup(private val sets: Map<String, SrsRuleSet>) :
    com.justnpcx.starveil.core.kernel.config.RuleSetLookup {

    private val regexCache = HashMap<String, Regex>()

    override fun matchesDomain(tag: String, domain: String): Boolean {
        val set = sets[tag] ?: return false
        val name = domain.trimEnd('.').lowercase()
        return set.rules.any { rule ->
            val hit = rule.matchesDomain(name, regexCache)
            if (rule.invert) !hit else hit
        }
    }

    override fun matchesIp(tag: String, ip: String): Boolean {
        val set = sets[tag] ?: return false
        val address = IpAddress.parse(ip) ?: return false
        return set.rules.any { rule ->
            val hit = rule.ipRanges.any { it.contains(address.high, address.low, address.isV6) }
            if (rule.invert) !hit else hit
        }
    }

    /** What is loaded, for the screen that shows the tool's inputs. */
    fun tags(): Set<String> = sets.keys
}

private fun SrsRule.matchesDomain(name: String, regexCache: MutableMap<String, Regex>): Boolean {
    if (exactDomains.any { it.equals(name, ignoreCase = true) }) return true
    for (suffix in domainSuffixes) {
        val value = suffix.lowercase()
        val hit = if (value.startsWith(".")) {
            // ".example.com" is the legacy form: subdomains only, not the apex.
            name.endsWith(value)
        } else {
            name == value || name.endsWith("." + value)
        }
        if (hit) return true
    }
    if (domainKeywords.any { name.contains(it.lowercase()) }) return true
    for (pattern in domainRegexes) {
        val regex = regexCache.getOrPut(pattern) {
            runCatching { Regex(pattern) }.getOrElse { return@getOrPut Regex("(?!)") }
        }
        if (regex.containsMatchIn(name)) return true
    }
    return false
}

/** A 128-bit address split in two, so a v4 range is one Long comparison. */
data class IpAddress(val high: Long, val low: Long, val isV6: Boolean) {

    companion object {
        fun parse(value: String): IpAddress? {
            val text = value.substringBefore('%')
            return if (text.contains(':')) parseV6(text) else parseV4(text)
        }

        private fun parseV4(value: String): IpAddress? {
            val parts = value.split('.')
            if (parts.size != 4) return null
            var result = 0L
            for (part in parts) {
                val byte = part.toIntOrNull() ?: return null
                if (byte !in 0..255) return null
                result = (result shl 8) or byte.toLong()
            }
            return IpAddress(0L, result, false)
        }

        /**
         * Groups of up to four hex digits, with one `::` standing for however
         * many zero groups are needed. An embedded IPv4 tail (`::ffff:1.2.3.4`)
         * is not accepted: it is a form the rule sets here do not use, and
         * guessing would be worse than not matching.
         */
        private fun parseV6(value: String): IpAddress? {
            val halves = value.split("::")
            if (halves.size > 2) return null
            val head = if (halves[0].isEmpty()) emptyList() else halves[0].split(':')
            val tail = if (halves.size == 1) emptyList() else halves[1].split(':').filter { it.isNotEmpty() }
            val missing = 8 - head.size - tail.size
            if (halves.size == 1 && missing != 0) return null
            if (missing < 0) return null
            val groups = head + List(missing) { "0" } + tail
            var high = 0L
            var low = 0L
            for ((index, group) in groups.withIndex()) {
                val part = group.toIntOrNull(16) ?: return null
                if (part !in 0..0xFFFF) return null
                if (index < 4) high = (high shl 16) or part.toLong()
                else low = (low shl 16) or part.toLong()
            }
            return IpAddress(high, low, true)
        }
    }
}

