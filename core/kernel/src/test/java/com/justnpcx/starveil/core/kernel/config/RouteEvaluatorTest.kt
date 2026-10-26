package com.justnpcx.starveil.core.kernel.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The evaluator is driven by the same list [ConfigBuilder.routeRules] emits, so
 * these tests build that list by hand in exactly the shape the builder does —
 * sniff first, then ads, then the preset, then `final`.
 */
class RouteEvaluatorTest {

    private val lookup = object : RuleSetLookup {
        override fun matchesDomain(tag: String, domain: String) = when (tag) {
            "ads" -> domain == "doubleclick.net" || domain.endsWith(".doubleclick.net")
            "geosite-cn" -> domain == "baidu.com" || domain.endsWith(".baidu.com")
            else -> false
        }

        override fun matchesIp(tag: String, ip: String) =
            tag == "geoip-cn" && ip.startsWith("223.5.")
    }

    private val rules: List<Map<String, Any?>> = listOf(
        linkedMapOf("action" to "sniff", "sniffer" to listOf("tls", "http", "quic")),
        linkedMapOf("rule_set" to "ads", "action" to "reject"),
        linkedMapOf("rule_set" to "geosite-cn", "action" to "route", "outbound" to "direct"),
        linkedMapOf("rule_set" to "geoip-cn", "action" to "route", "outbound" to "direct"),
    )

    @Test
    fun `an ad domain is rejected by the ad list, and the sniff rule is skipped`() {
        val decision = RouteEvaluator.evaluate(rules, "proxy", "doubleclick.net", null, lookup)

        assertEquals(1, decision.ruleIndex)
        assertEquals("rule_set=ads", decision.ruleLabel)
        assertEquals("reject", decision.outbound)
        assertEquals(DecisionReason.FIRST_MATCH, decision.reason)
    }

    @Test
    fun `a domestic domain falls through to the preset and goes direct`() {
        val decision = RouteEvaluator.evaluate(rules, "proxy", "www.baidu.com", null, lookup)

        assertEquals(2, decision.ruleIndex)
        assertEquals("rule_set=geosite-cn", decision.ruleLabel)
        assertEquals("direct", decision.outbound)
        assertEquals(DecisionReason.PRESET_FALLBACK, decision.reason)
    }

    @Test
    fun `a domestic address matches by IP when there is no name`() {
        val decision = RouteEvaluator.evaluate(rules, "proxy", null, "223.5.5.5", lookup)

        assertEquals(3, decision.ruleIndex)
        assertEquals("direct", decision.outbound)
    }

    @Test
    fun `anything else ends at final, which is the proxy`() {
        val decision = RouteEvaluator.evaluate(rules, "proxy", "example.com", null, lookup)

        assertNull(decision.ruleIndex)
        assertEquals("final", decision.ruleLabel)
        assertEquals("proxy", decision.outbound)
        assertEquals(DecisionReason.DEFAULT_OUTBOUND, decision.reason)
    }

    @Test
    fun `a suffix without a leading dot covers the name itself and its children`() {
        val suffixRules = listOf(
            linkedMapOf("domain_suffix" to listOf("example.com"), "action" to "route", "outbound" to "proxy"),
        )

        assertEquals("proxy", RouteEvaluator.evaluate(suffixRules, "direct", "example.com", null, lookup).outbound)
        assertEquals("proxy", RouteEvaluator.evaluate(suffixRules, "direct", "a.example.com", null, lookup).outbound)
        assertEquals("direct", RouteEvaluator.evaluate(suffixRules, "direct", "notexample.com", null, lookup).outbound)
    }
}

