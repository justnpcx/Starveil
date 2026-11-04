package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher

/**
 * How a rule reads in the kernel's own vocabulary.
 *
 * This is the single producer of the text that appears in `Connection.rule`.
 * sing-box renders a rule as `field=value` for a one-value matcher and
 * `field=[a b c]` for several (`route/rule/rule_item_domain.go`), and
 * `rule_set=<tag>` for a set. The hit counters are keyed by exactly this text,
 * so a second implementation anywhere — a UI joining "kind + '=' + value" by
 * hand — would drift the moment either side changed and silently show zero
 * hits. Both the dry-run label and the hit join call this.
 */
object RuleLabels {

    /** `reject` is sing-box's own outbound name for a rejected rule. */
    const val REJECT = "reject"

    /** The label of a rule map, as the kernel would print it. */
    fun of(rule: Map<String, Any?>): String {
        (rule["rule_set"] as? String)?.let { return ruleSet(it) }
        val key = rule.keys.firstOrNull { it != "action" && it != "outbound" }
        val value = key?.let { rule[it] } as? List<*>
        val first = value?.firstOrNull()?.toString()
        return if (key != null && first != null) key + "=" + first else "rule"
    }

    /**
     * The label of a user rule's matcher.
     *
     * Built through [of] rather than by concatenation: one code path means the
     * text a count is stored under is, by construction, the text the kernel
     * prints for the same rule.
     */
    fun matcherText(matcher: RuleMatcher): String =
        of(linkedMapOf(matcher.kind.wireName to listOf(matcher.value)))

    fun ruleSet(tag: String): String = "rule_set=" + tag

    /** The outbound tag a user rule's action selects, under this preset. */
    fun outboundFor(action: RuleAction, proxyTag: String): String = when (action) {
        RuleAction.PROXY -> proxyTag
        RuleAction.DIRECT -> "direct"
        RuleAction.BLOCK -> REJECT
    }
}
