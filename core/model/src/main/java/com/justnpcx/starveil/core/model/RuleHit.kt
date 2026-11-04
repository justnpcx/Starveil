package com.justnpcx.starveil.core.model

/**
 * How many connections one routing decision carried, since the window opened.
 *
 * The key is the kernel's own rendering of the rule (`rule_set=ads`,
 * `domain_suffix=example.com`) plus the outbound tag it selected. Both come
 * from the kernel's connection events, so a hit can never be attributed to a
 * rule by guesswork: [ruleText] is what the kernel said matched.
 *
 * Deliberately no domain, address or app: a count answers "is this rule doing
 * anything", and keeping the destination would turn a diagnostic into a
 * browsing history. Same privacy line as the diagnostic bundle.
 */
data class RuleHit(
    val ruleText: String,
    val outbound: String,
    val count: Long,
    val lastHitAtMs: Long,
)
