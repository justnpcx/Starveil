package com.justnpcx.starveil.core.kernel.ruleset

import com.justnpcx.starveil.core.kernel.config.RouteEvaluator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import com.justnpcx.starveil.core.model.ruleset.Srs

/**
 * The whole chain on the real files the app ships with: three sets parsed from
 * disk, looked up by tag, and run through the evaluator's walk.
 *
 * Local only — CI has no rule sets and should not download any. Point
 * SRS_ADS / SRS_CN / SRS_GEOIP at the three `.srs` files to run it.
 */
class SrsEndToEndTest {

    @Test
    fun `the three real sets answer an ad, a domestic name and a foreign one`() {
        val ads = System.getenv("SRS_ADS")
        val cn = System.getenv("SRS_CN")
        val geoip = System.getenv("SRS_GEOIP")
        assumeTrue("set SRS_ADS / SRS_CN / SRS_GEOIP", ads != null && cn != null && geoip != null)

        val lookup = SrsRuleSetLookup(
            mapOf(
                "ads" to Srs.read(File(ads!!).readBytes()),
                "geosite-cn" to Srs.read(File(cn!!).readBytes()),
                "geoip-cn" to Srs.read(File(geoip!!).readBytes()),
            ),
        )
        val rules = listOf(
            linkedMapOf("action" to "sniff", "sniffer" to listOf("tls", "http", "quic")),
            linkedMapOf("rule_set" to "ads", "action" to "reject"),
            linkedMapOf("rule_set" to "geosite-cn", "action" to "route", "outbound" to "direct"),
            linkedMapOf("rule_set" to "geoip-cn", "action" to "route", "outbound" to "direct"),
        )

        val ad = RouteEvaluator.evaluate(rules, "proxy", "doubleclick.net", null, lookup)
        assertEquals("reject", ad.outbound)
        assertEquals("rule_set=ads", ad.ruleLabel)

        val domestic = RouteEvaluator.evaluate(rules, "proxy", "www.baidu.com", null, lookup)
        assertEquals("direct", domestic.outbound)
        assertEquals("rule_set=geosite-cn", domestic.ruleLabel)

        val foreign = RouteEvaluator.evaluate(rules, "proxy", "example.com", null, lookup)
        assertEquals("proxy", foreign.outbound)
        assertEquals("final", foreign.ruleLabel)

        val byAddress = RouteEvaluator.evaluate(rules, "proxy", null, "223.5.5.5", lookup)
        assertEquals("direct", byAddress.outbound)
        assertEquals("rule_set=geoip-cn", byAddress.ruleLabel)
    }
}

