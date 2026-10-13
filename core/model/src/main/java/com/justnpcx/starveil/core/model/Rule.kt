package com.justnpcx.starveil.core.model

/**
 * A routing rule: match [matcher] -> [action].
 *
 * Skeleton keeps matchers as plain strings (domain / IP / keyword).
 * The real implementation replaces [matcher] with a typed sealed hierarchy
 * (DomainSuffix / IpCidr / Keyword / RuleSetRef) — screens render the
 * summary via [summary], so UI never branches on matcher internals.
 */
data class Rule(
    val id: Long = 0L,
    val matcher: String,
    val action: RuleAction,
    val order: Int = 0,
    val enabled: Boolean = true,
) {
    val summary: String get() = "$matcher → $action"
}

enum class RuleAction {
    PROXY,
    DIRECT,
    BLOCK,
}
