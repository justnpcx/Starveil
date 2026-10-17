package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker.
 *
 * Three rules, and each has a test that fails if it is dropped: reliability
 * outranks speed, an exact tie goes to the preferred region, and a node that
 * has just failed three times in a row is out of the running for a while.
 */
class NodePickerTest {

    private fun candidate(
        id: Long,
        health: NodeHealth?,
        region: NodeRegion? = null,
    ) = NodeCandidate(profileId = id, region = region, health = health)

    @Test
    fun `the healthier node wins even when the other is much faster`() {
        val steady = candidate(1, healthOf(reachable = 10, latency = 400))
        val flaky = candidate(2, healthOf(reachable = 9, failed = 1, latency = 50))

        val pick = NodePicker.recommend(listOf(flaky, steady), nowMs = NOW)

        // Reliability is the high-order part of the score: 100% at 400 ms beats
        // 90% at 50 ms, because a node that drops one connection in ten is
        // worse than a slow one that never does.
        assertEquals(1L, pick?.profileId)
        assertTrue(pick!!.reason is RecommendationReason.Healthiest)
    }

    @Test
    fun `an exact tie goes to the preferred region`() {
        val hk = candidate(1, healthOf(reachable = 10), NodeRegion.HONG_KONG)
        val jp = candidate(2, healthOf(reachable = 10), NodeRegion.JAPAN)

        val pick = NodePicker.recommend(
            listOf(hk, jp),
            preferredRegion = NodeRegion.JAPAN,
            nowMs = NOW,
        )

        assertEquals(2L, pick?.profileId)
        assertEquals(
            RecommendationReason.TiedOnScore(pick!!.score, NodeRegion.JAPAN),
            pick.reason,
        )
    }

    @Test
    fun `an exact tie without a preference is stable`() {
        val a = candidate(7, healthOf(reachable = 10))
        val b = candidate(3, healthOf(reachable = 10))

        val pick = NodePicker.recommend(listOf(a, b), nowMs = NOW)

        // Lowest id, so the same input always picks the same node.
        assertEquals(3L, pick?.profileId)
        assertEquals(RecommendationReason.TiedOnScore(pick!!.score, null), pick.reason)
    }

    @Test
    fun `a preferred region does not beat a much healthier node`() {
        val sick = candidate(1, healthOf(reachable = 8, failed = 2), NodeRegion.JAPAN)
        val healthy = candidate(2, healthOf(reachable = 10), NodeRegion.HONG_KONG)

        val pick = NodePicker.recommend(
            listOf(sick, healthy),
            preferredRegion = NodeRegion.JAPAN,
            nowMs = NOW,
        )

        // Region is a tie-break, not a filter.
        assertEquals(2L, pick?.profileId)
    }

    @Test
    fun `three failures in a row take a node out even though it scores higher`() {
        // 97% availability and fast: it would win on score alone.
        val justFailed = candidate(1, healthOf(reachable = 97, failed = 3, latency = 50))
        val worse = candidate(2, healthOf(reachable = 8, failed = 2, latency = 300))

        val pick = NodePicker.recommend(listOf(justFailed, worse), nowMs = NOW)

        assertEquals(2L, pick?.profileId)
    }

    @Test
    fun `two failures are not a time-out`() {
        val twoFailures = candidate(1, healthOf(reachable = 97, failed = 2, latency = 50))
        val worse = candidate(2, healthOf(reachable = 8, failed = 2, latency = 300))

        val pick = NodePicker.recommend(listOf(twoFailures, worse), nowMs = NOW)

        assertEquals(1L, pick?.profileId)
    }

    @Test
    fun `the time-out ends after thirty minutes`() {
        val failedLongAgo = candidate(
            1,
            healthOf(reachable = 97, failed = 3, latency = 50, endAtMs = NOW - 31 * 60_000L),
        )
        val worse = candidate(2, healthOf(reachable = 8, failed = 2, latency = 300))

        val pick = NodePicker.recommend(listOf(failedLongAgo, worse), nowMs = NOW)

        // Eligible again, and it wins on score.
        assertEquals(1L, pick?.profileId)
    }

    @Test
    fun `when everything is cooling down the soonest recovery wins`() {
        val almostBack = candidate(
            1,
            healthOf(reachable = 0, failed = 3, endAtMs = NOW - 25 * 60_000L),
        )
        val justFailed = candidate(
            2,
            healthOf(reachable = 0, failed = 3, endAtMs = NOW - 1 * 60_000L),
        )

        val pick = NodePicker.recommend(listOf(justFailed, almostBack), nowMs = NOW)

        assertEquals(1L, pick?.profileId)
        val reason = pick!!.reason as RecommendationReason.AllCoolingDown
        assertTrue("expected a few minutes left, got ${reason.minutesLeft}", reason.minutesLeft in 4..6)
    }

    @Test
    fun `an untested node is picked only when it is all there is`() {
        val pick = NodePicker.recommend(listOf(candidate(1, health = null)), nowMs = NOW)

        assertEquals(1L, pick?.profileId)
        assertEquals(RecommendationReason.NothingMeasured, pick?.reason)
        assertEquals(0, pick?.score)
    }

    @Test
    fun `an untested node never outranks a measured one`() {
        val measured = candidate(1, healthOf(reachable = 10, latency = 400))
        val untested = candidate(2, health = null)

        val pick = NodePicker.recommend(listOf(untested, measured), nowMs = NOW)

        assertEquals(1L, pick?.profileId)
    }

    @Test
    fun `an empty list has no answer`() {
        assertNull(NodePicker.recommend(emptyList(), nowMs = NOW))
    }

    @Test
    fun `a manual failure is half a failure for the time-out`() {
        fun failures(count: Int, weight: Int) = nodeHealthOf(
            (0 until count).map {
                LatencySample(
                    atMs = NOW - (count - it) * 1_000L,
                    latencyMs = LATENCY_UNREACHABLE,
                    weightTenths = weight,
                )
            },
        )

        // Three taps are not a pattern; six are worth as much as three sweeps.
        assertEquals(0L, cooldownRemainingMs(failures(3, WEIGHT_MANUAL), NOW))
        assertTrue(cooldownRemainingMs(failures(6, WEIGHT_MANUAL), NOW) > 0)
        assertTrue(cooldownRemainingMs(failures(3, WEIGHT_FULL), NOW) > 0)
    }

    @Test
    fun `the score puts availability above latency`() {
        val reliable = healthScore(healthOf(reachable = 10, latency = 400))
        val fast = healthScore(healthOf(reachable = 9, failed = 1, latency = 50))

        assertTrue("reliable=$reliable fast=$fast", reliable > fast)
    }

    @Test
    fun `a node with no samples scores zero`() {
        assertEquals(0, healthScore(healthOf(reachable = 0, failed = 0)))
        assertEquals(0, healthScore(null))
    }

    private companion object {
        const val NOW = 10_000_000L
    }
}

/**
 * Samples ending at [endAtMs], oldest first: [reachable] successes followed by
 * [failed] failures. The failures are last because that is what "in a row"
 * means to the cooldown.
 */
internal fun healthOf(
    reachable: Int,
    failed: Int = 0,
    latency: Long = 60,
    endAtMs: Long = 10_000_000L,
): NodeHealth {
    val total = reachable + failed
    val samples = buildList {
        repeat(reachable) {
            add(LatencySample(endAtMs - (total - 1 - size) * 1_000L, latency))
        }
        repeat(failed) {
            add(LatencySample(endAtMs - (total - 1 - size) * 1_000L, LATENCY_UNREACHABLE))
        }
    }
    return nodeHealthOf(samples)
}
