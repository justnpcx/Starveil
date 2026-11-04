package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.kernel.ConnectionEventKind
import com.justnpcx.starveil.core.kernel.KernelConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleHitAccumulatorTest {

    private fun event(
        id: String = "1",
        rule: String = "rule_set=ads",
        outbound: String = "reject",
        kind: ConnectionEventKind = ConnectionEventKind.NEW,
    ) = KernelConnection(
        id = id,
        destination = "",
        domain = "",
        rule = rule,
        outbound = outbound,
        outboundType = "",
        createdAtMs = 0L,
        event = kind,
    )

    @Test
    fun `only a new event counts`() {
        val accumulator = RuleHitAccumulator()

        accumulator.record(event(kind = ConnectionEventKind.NEW))
        accumulator.record(event(kind = ConnectionEventKind.UPDATE))
        accumulator.record(event(kind = ConnectionEventKind.UPDATE))
        accumulator.record(event(kind = ConnectionEventKind.CLOSE))

        val deltas = accumulator.drain()
        assertEquals(1, deltas.size)
        assertEquals(1L, deltas.single().count)
    }

    @Test
    fun `counts are keyed by rule text and outbound`() {
        val accumulator = RuleHitAccumulator()

        accumulator.record(event(id = "1", rule = "rule_set=ads", outbound = "reject"))
        accumulator.record(event(id = "2", rule = "rule_set=ads", outbound = "reject"))
        // Same matcher, different action: a different decision.
        accumulator.record(event(id = "3", rule = "rule_set=ads", outbound = "proxy"))

        val byKey = accumulator.drain().associate { (it.ruleText to it.outbound) to it.count }
        assertEquals(2L, byKey["rule_set=ads" to "reject"])
        assertEquals(1L, byKey["rule_set=ads" to "proxy"])
    }

    @Test
    fun `a decision with no rule text is not counted`() {
        val accumulator = RuleHitAccumulator()

        accumulator.record(event(rule = ""))
        accumulator.record(event(rule = "   "))

        assertTrue(accumulator.drain().isEmpty())
    }

    @Test
    fun `the buffer asks for a flush at the threshold and empties on drain`() {
        val accumulator = RuleHitAccumulator(flushAt = 3)

        assertFalse(accumulator.record(event(id = "1", rule = "domain=example.com")))
        assertFalse(accumulator.record(event(id = "2", rule = "domain=example.com")))
        assertTrue(accumulator.record(event(id = "3", rule = "domain=example.com")))

        assertEquals(3L, accumulator.drain().single().count)
        assertTrue(accumulator.drain().isEmpty())
        assertFalse(accumulator.record(event(id = "4", rule = "domain=example.com")))
    }
}
