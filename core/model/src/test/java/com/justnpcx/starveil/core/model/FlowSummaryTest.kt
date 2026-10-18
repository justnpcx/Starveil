package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Grouping is what lets fifty connections fit on one screen, so the two
 * properties the screen relies on are pinned here: one row per (rule, outcome),
 * and the biggest group first.
 */
class FlowSummaryTest {

    private fun record(id: String, rule: String, outcome: FlowOutcome, at: Long) =
        FlowRecord(
            connectionId = id,
            destination = "example.com:443",
            domain = "",
            rule = rule,
            outcome = outcome,
            createdAtMs = at,
        )

    @Test
    fun `one row per rule and outcome, with a count`() {
        val groups = groupFlow(
            listOf(
                record("1", "rule_set=geosite-cn", FlowOutcome.DIRECT, 1_000),
                record("2", "rule_set=geosite-cn", FlowOutcome.DIRECT, 2_000),
                record("3", "rule_set=geosite-cn", FlowOutcome.BLOCK, 3_000),
            ),
        )

        assertEquals(2, groups.size)
        val direct = groups.first { it.outcome == FlowOutcome.DIRECT }
        assertEquals(2, direct.count)
        assertEquals("rule_set=geosite-cn", direct.rule)
    }

    @Test
    fun `the biggest group comes first, then the newest`() {
        val groups = groupFlow(
            listOf(
                record("1", "ads", FlowOutcome.BLOCK, 1_000),
                record("2", "cn", FlowOutcome.DIRECT, 2_000),
                record("3", "cn", FlowOutcome.DIRECT, 3_000),
                record("4", "cn", FlowOutcome.DIRECT, 4_000),
                record("5", "other", FlowOutcome.PROXY, 5_000),
                record("6", "other2", FlowOutcome.PROXY, 6_000),
            ),
        )

        assertEquals("cn", groups.first().rule)
        assertEquals(3, groups.first().count)
        // Two groups of one; the newer of them comes first.
        assertEquals("other2", groups[1].rule)
        assertEquals("other", groups[2].rule)
    }

    @Test
    fun `an unnamed rule is its own group`() {
        val groups = groupFlow(
            listOf(
                record("1", "", FlowOutcome.PROXY, 1_000),
                record("2", "", FlowOutcome.PROXY, 2_000),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals("", groups.single().rule)
        assertEquals(2, groups.single().count)
    }
}
