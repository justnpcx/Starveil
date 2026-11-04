package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The join between a stored count and the rule a screen shows is text
 * equality, so the producer of that text has to be the same one the kernel's
 * own rendering is modelled on — and the same one the dry run uses.
 */
class RuleLabelsTest {

    @Test
    fun `a one-value matcher reads as field equals value`() {
        assertEquals(
            "domain_suffix=example.com",
            RuleLabels.matcherText(RuleMatcher(MatcherKind.DOMAIN_SUFFIX, "example.com")),
        )
        assertEquals(
            "ip_cidr=10.0.0.0/8",
            RuleLabels.matcherText(RuleMatcher(MatcherKind.IP_CIDR, "10.0.0.0/8")),
        )
    }

    @Test
    fun `the dry run labels a rule exactly as the join does`() {
        val rule = mapOf<String, Any?>(
            "domain_keyword" to listOf("doubleclick"),
            "action" to "reject",
        )
        assertEquals("domain_keyword=doubleclick", RuleLabels.of(rule))
        assertEquals(
            RuleLabels.matcherText(RuleMatcher(MatcherKind.DOMAIN_KEYWORD, "doubleclick")),
            RuleLabels.of(rule),
        )
    }

    @Test
    fun `a rule set is labelled by its tag`() {
        assertEquals("rule_set=geosite-cn", RuleLabels.of(mapOf("rule_set" to "geosite-cn")))
        assertEquals("rule_set=ads", RuleLabels.ruleSet(ConfigBuilder.ADS_TAG))
    }

    @Test
    fun `the outbound follows the action under the preset`() {
        assertEquals("proxy", RuleLabels.outboundFor(RuleAction.PROXY, "proxy"))
        assertEquals("direct", RuleLabels.outboundFor(RuleAction.PROXY, "direct"))
        assertEquals("direct", RuleLabels.outboundFor(RuleAction.DIRECT, "proxy"))
        assertEquals("reject", RuleLabels.outboundFor(RuleAction.BLOCK, "proxy"))
    }
}
