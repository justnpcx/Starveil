package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The node health maths. Pure by construction — no clock, no database — so
 * these are the whole verification of the scoring the audit asked for.
 */
class NodeHealthTest {

    private fun samples(vararg latencies: Long): List<LatencySample> =
        latencies.mapIndexed { index, ms -> LatencySample(atMs = index * 1_000L, latencyMs = ms) }

    @Test
    fun `the ema seeds on the first success and smooths from there`() {
        // 100, then 0.2*200 + 0.8*100 = 120, then 0.2*300 + 0.8*120 = 156.
        assertEquals(156L, emaMs(samples(100, 200, 300)))
    }

    @Test
    fun `a failed probe does not drag the ema toward zero`() {
        // The failure is skipped, not read as a latency of zero: otherwise an
        // unreachable node would look faster the worse it got.
        assertEquals(120L, emaMs(samples(100, -1, 200)))
        assertEquals(120L, emaMs(samples(100, -1, -1, 200)))
    }

    @Test
    fun `an ema needs at least one success`() {
        assertNull(emaMs(samples(-1, -1, -1)))
        assertNull(emaMs(emptyList()))
    }

    @Test
    fun `the ema series is null until the first success, then carries forward`() {
        // null, null, then 200 seeded, then 0.2*300 + 0.8*200 = 220.
        assertEquals(listOf(null, null, 200L, 220L), emaSeries(samples(-1, -1, 200, 300)))
    }

    @Test
    fun `a failure carries the ema forward rather than breaking the curve`() {
        // The loss is the dot below the curve; a gap in the line would read as
        // "no reading" instead of "no answer".
        assertEquals(listOf(100L, 100L, 120L), emaSeries(samples(100, -1, 200)))
    }

    @Test
    fun `the scalar ema is the series' last entry`() {
        val samples = samples(100, -1, 200, -1)
        assertEquals(emaSeries(samples).last(), emaMs(samples))
    }

    @Test
    fun `a half-weight sample moves the ema half as far`() {
        // Seed at 100, then a manual 300: 0.1 * 300 + 0.9 * 100 = 120, where a
        // full-weight 300 would have given 140.
        val samples = listOf(
            LatencySample(atMs = 0, latencyMs = 100),
            LatencySample(atMs = 1_000, latencyMs = 300, weightTenths = WEIGHT_MANUAL),
        )

        assertEquals(120L, emaMs(samples))
    }

    @Test
    fun `availability counts a half-weight sample as half`() {
        val samples = listOf(
            LatencySample(atMs = 0, latencyMs = 10),
            LatencySample(atMs = 1_000, latencyMs = -1, weightTenths = WEIGHT_MANUAL),
        )

        // 10 reachable out of 15 weight: a full failure would have given 50%.
        assertEquals(66.67f, availabilityPct(samples), 0.01f)
    }

    @Test
    fun `availability is the share of the window that answered`() {
        assertEquals(100f, availabilityPct(samples(1, 2, 3)))
        assertEquals(50f, availabilityPct(samples(1, -1, -1, 2)))
        assertEquals(0f, availabilityPct(samples(-1, -1)))
        assertEquals(0f, availabilityPct(emptyList()))
    }

    @Test
    fun `band boundaries do not flatter the node`() {
        assertEquals(AvailabilityBand.GOOD, bandOf(100f))
        assertEquals(AvailabilityBand.GOOD, bandOf(99.1f))
        // Exactly 99 is not "above 99".
        assertEquals(AvailabilityBand.FAIR, bandOf(99f))
        assertEquals(AvailabilityBand.FAIR, bandOf(95f))
        assertEquals(AvailabilityBand.POOR, bandOf(94.9f))
        assertEquals(AvailabilityBand.POOR, bandOf(0f))
    }

    @Test
    fun `collecting flips at the fifth sample`() {
        assertTrue(nodeHealthOf(samples(1, 2, 3, 4)).collecting)
        assertFalse(nodeHealthOf(samples(1, 2, 3, 4, 5)).collecting)
    }

    @Test
    fun `an empty window is collecting and untested`() {
        val health = nodeHealthOf(emptyList())
        assertTrue(health.collecting)
        assertNull(health.emaMs)
        assertEquals(0f, health.availabilityPct)
        assertEquals(NodeStatus.UNTESTED, health.status)
        assertNull(health.latest)
    }

    @Test
    fun `the status dot follows the latest probe and the band`() {
        assertEquals(NodeStatus.UP, nodeHealthOf(samples(100, 90, 80, 70, 60)).status)
        // The last probe failed, whatever the window looked like before.
        assertEquals(NodeStatus.DOWN, nodeHealthOf(samples(100, 90, 80, 70, -1)).status)
        // Answering now, but the window is mostly loss: still red.
        val lossy = List(20) { LatencySample(atMs = it * 1_000L, latencyMs = -1) } +
            LatencySample(atMs = 20_000L, latencyMs = 50)
        val health = nodeHealthOf(lossy)
        assertEquals(AvailabilityBand.POOR, health.band)
        assertTrue("the latest probe answered", health.latest?.reachable == true)
        assertEquals(NodeStatus.DOWN, health.status)
    }

    @Test
    fun `samples are sorted by time regardless of the caller's order`() {
        val shuffled = listOf(
            LatencySample(atMs = 3_000L, latencyMs = 30),
            LatencySample(atMs = 1_000L, latencyMs = 10),
            LatencySample(atMs = 2_000L, latencyMs = 20),
        )
        val health = nodeHealthOf(shuffled)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), health.samples.map { it.atMs })
        // "The latest probe" is only meaningful once ordered.
        assertEquals(3_000L, health.latest?.atMs)
    }
}
