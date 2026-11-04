package com.justnpcx.starveil.feature.settings

import com.justnpcx.starveil.core.model.RuleHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three lists the diagnostics card shows, as pure functions of the counts
 * and the rules in force.
 */
class RuleHitsStateTest {

    private fun hit(rule: String, outbound: String = "reject", count: Long = 1) =
        RuleHit(rule, outbound, count, 1_000L)

    @Test
    fun `a rule that never matched is named, not counted`() {
        val state = RuleHitsState(
            hits = listOf(hit("rule_set=ads", count = 7)),
            knownLabels = listOf("rule_set=ads", "rule_set=geosite-cn"),
        )

        assertEquals(listOf("rule_set=geosite-cn"), state.zeroHit)
    }

    @Test
    fun `a count with no rule in force is kept as raw text`() {
        val state = RuleHitsState(
            hits = listOf(hit("rule_set=deleted-pack"), hit("rule_set=ads")),
            knownLabels = listOf("rule_set=ads"),
        )

        assertEquals(listOf("rule_set=deleted-pack"), state.unmapped.map { it.ruleText })
        // It is not silently dropped from the distribution either.
        assertTrue(state.top.any { it.ruleText == "rule_set=deleted-pack" })
    }

    @Test
    fun `the busiest few are shown, already ordered by the query`() {
        val hits = (1..8).map { hit("domain=$it.example", count = it.toLong()) }
        val state = RuleHitsState(hits = hits, knownLabels = emptyList())

        assertEquals(RuleHitsState.TOP_HITS, state.top.size)
        assertEquals("domain=1.example", state.top.first().ruleText)
    }

    @Test
    fun `nothing counted yet means every rule is a zero hit, not an error`() {
        val state = RuleHitsState(knownLabels = listOf("rule_set=ads"))

        assertEquals(listOf("rule_set=ads"), state.zeroHit)
        assertTrue(state.top.isEmpty())
        assertTrue(state.unmapped.isEmpty())
    }
}
