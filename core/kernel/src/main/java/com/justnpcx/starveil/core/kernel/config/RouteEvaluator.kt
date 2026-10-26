package com.justnpcx.starveil.core.kernel.config

interface RuleSetLookup {
    fun matchesDomain(tag: String, domain: String): Boolean
    fun matchesIp(tag: String, ip: String): Boolean
}

enum class DecisionReason { FIRST_MATCH, PRESET_FALLBACK, DEFAULT_OUTBOUND }

data class RouteDecision(
    val ruleIndex: Int?,
    val ruleLabel: String,
    val outbound: String,
    val reason: DecisionReason,
)

/**
 * Walks the rule list [ConfigBuilder] emits, in order, and reports the first
 * match. A dry run: it takes a name and an address, never opens a socket,
 * never touches the tunnel, never changes a rule.
 */
object RouteEvaluator {

    private const val REJECT = "reject"

    fun evaluate(
        rules: List<Map<String, Any?>>,
        finalTag: String,
        domain: String?,
        ip: String?,
        lookup: RuleSetLookup,
    ): RouteDecision {
        rules.forEachIndexed { index, rule ->
            if (!matches(rule, domain, ip, lookup)) return@forEachIndexed
            val action = rule["action"] as? String ?: return@forEachIndexed
            val outbound = when (action) {
                "reject" -> REJECT
                "route" -> rule["outbound"] as? String ?: finalTag
                else -> return@forEachIndexed
            }
            val label = labelOf(rule)
            val reason = when (label) {
                "rule_set=geosite-cn", "rule_set=geoip-cn" -> DecisionReason.PRESET_FALLBACK
                else -> DecisionReason.FIRST_MATCH
            }
            return RouteDecision(index, label, outbound, reason)
        }
        return RouteDecision(null, "final", finalTag, DecisionReason.DEFAULT_OUTBOUND)
    }

    private fun matches(
        rule: Map<String, Any?>,
        domain: String?,
        ip: String?,
        lookup: RuleSetLookup,
    ): Boolean {
        var tested = false
        (rule["domain"] as? List<*>)?.let { values ->
            tested = true
            if (domain != null && values.any { it == domain }) return true
        }
        (rule["domain_suffix"] as? List<*>)?.let { values ->
            tested = true
            if (domain != null && values.any { suffix ->
                    val value = suffix as String
                    if (value.startsWith(".")) {
                        domain.endsWith(value)
                    } else {
                        domain == value || domain.endsWith("." + value)
                    }
                }
            ) return true
        }
        (rule["domain_keyword"] as? List<*>)?.let { values ->
            tested = true
            if (domain != null && values.any { domain.contains(it as String) }) return true
        }
        (rule["ip_cidr"] as? List<*>)?.let { values ->
            tested = true
            if (ip != null && values.any { cidrContains(it as String, ip) }) return true
        }
        (rule["rule_set"] as? String)?.let { tag ->
            tested = true
            if (domain != null && lookup.matchesDomain(tag, domain)) return true
            if (ip != null && lookup.matchesIp(tag, ip)) return true
        }
        return !tested
    }

    private fun labelOf(rule: Map<String, Any?>): String {
        (rule["rule_set"] as? String)?.let { return "rule_set=" + it }
        val key = rule.keys.firstOrNull { it != "action" && it != "outbound" }
        val value = key?.let { rule[it] } as? List<*>
        val first = value?.firstOrNull()?.toString()
        return if (key != null && first != null) key + "=" + first else "rule"
    }

    private fun cidrContains(cidr: String, ip: String): Boolean {
        val slash = cidr.indexOf('/')
        if (slash < 0) return cidr == ip
        if (cidr.contains(':')) return false
        val base = ipv4(cidr.substring(0, slash)) ?: return false
        val candidate = ipv4(ip) ?: return false
        val bits = cidr.substring(slash + 1).toIntOrNull() ?: return false
        if (bits !in 0..32) return false
        val mask = if (bits == 0) 0L else (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
        return (base and mask) == (candidate and mask)
    }

    private fun ipv4(value: String): Long? {
        val parts = value.split('.')
        if (parts.size != 4) return null
        var result = 0L
        for (part in parts) {
            val byte = part.toIntOrNull() ?: return null
            if (byte !in 0..255) return null
            result = (result shl 8) or byte.toLong()
        }
        return result
    }
}

