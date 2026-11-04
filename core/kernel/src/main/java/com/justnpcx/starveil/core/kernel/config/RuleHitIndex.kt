package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleHit

/**
 * Joins stored counts to the rules a screen is showing.
 *
 * The join key is (kernel rule text, outbound tag) — exactly what the kernel
 * reported and what [RuleLabels] produces. Nothing here parses user input or
 * guesses: a rule whose text does not match is simply not joined, and its count
 * stays visible in the diagnostics distribution as raw text.
 */
object RuleHitIndex {

    /** Counts keyed by the pair they were stored under. */
    fun byKey(hits: List<RuleHit>): Map<Pair<String, String>, Long> =
        hits.associate { (it.ruleText to it.outbound) to it.count }

    /** The key a user rule's counts would be stored under, under this preset. */
    fun keyFor(rule: Rule, proxyTag: String): Pair<String, String> =
        RuleLabels.matcherText(rule.matcher) to RuleLabels.outboundFor(rule.action, proxyTag)

    /** The key a rule set's counts are stored under. */
    fun keyForRuleSet(tag: String, outbound: String): Pair<String, String> =
        RuleLabels.ruleSet(tag) to outbound
}
