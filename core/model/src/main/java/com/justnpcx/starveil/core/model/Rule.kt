package com.justnpcx.starveil.core.model

/**
 * What a routing rule matches on.
 *
 * [kind] decides which sing-box rule field the value goes into
 * (`domain` / `domain_suffix` / `domain_keyword` / `ip_cidr`), so neither the
 * UI nor the config builder has to parse strings.
 *
 * The set is deliberately small and honest: it is what sing-box 1.14 still
 * supports. `geoip` and `geosite` are NOT here — this fork removed them, and
 * a rule carrying one fails config construction outright ("geoip database is
 * deprecated in sing-box 1.8.0 and removed in sing-box 1.12.0"), which would
 * stop the kernel instead of filtering anything.
 */
data class RuleMatcher(
    val kind: MatcherKind,
    val value: String,
) {
    /** Storage form. The database column is a single string, so no schema change. */
    fun encode(): String = "${kind.wireName}:$value"

    companion object {
        /**
         * Tolerant, because the column has held plain strings: a value with no
         * recognised prefix is read as a domain suffix, which is what a bare
         * `example.com` means to a user.
         */
        fun decode(raw: String): RuleMatcher {
            val trimmed = raw.trim()
            val colon = trimmed.indexOf(':')
            if (colon <= 0) return RuleMatcher(MatcherKind.DOMAIN_SUFFIX, trimmed)
            val kind = MatcherKind.fromWireName(trimmed.substring(0, colon))
                ?: return RuleMatcher(MatcherKind.DOMAIN_SUFFIX, trimmed)
            return RuleMatcher(kind, trimmed.substring(colon + 1))
        }
    }
}

/** The matcher kinds sing-box still accepts. Labels live in the UI's strings. */
enum class MatcherKind(val wireName: String) {
    DOMAIN("domain"),
    DOMAIN_SUFFIX("domain_suffix"),
    DOMAIN_KEYWORD("domain_keyword"),
    IP_CIDR("ip_cidr"),
    ;

    companion object {
        fun fromWireName(name: String): MatcherKind? =
            entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) }
    }
}

/**
 * A routing rule: match [matcher] -> [action].
 *
 * Rules are evaluated in [order]; the first match wins, so the order the user
 * arranges is the order sing-box sees.
 */
data class Rule(
    val id: Long = 0L,
    val matcher: RuleMatcher,
    val action: RuleAction,
    val order: Int = 0,
    val enabled: Boolean = true,
)

enum class RuleAction {
    PROXY,
    DIRECT,
    BLOCK,
}
