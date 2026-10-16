package com.justnpcx.starveil.feature.profiles

import com.justnpcx.starveil.core.model.LatencySample
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.nodeHealthOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which number the node card shows (§6.3).
 *
 * The card has three possible sources for a latency — the curve, the last
 * manual measurement, and nothing — and picking the wrong one is the kind of
 * bug that looks like a working screen. Pure, so it is pinned here.
 */
class LatencyReadingTest {

    private fun profile(latencyMs: Long) = Profile(
        id = 1L,
        name = "node",
        type = ProfileType.VLESS,
        server = "n.example",
        port = 443,
        latencyMs = latencyMs,
    )

    private fun health(vararg latencies: Long) = nodeHealthOf(
        latencies.mapIndexed { index, ms -> LatencySample(atMs = index * 1_000L, latencyMs = ms) },
    )

    @Test
    fun `a node nobody has measured reads as untested`() {
        assertEquals(LatencyReading.Untested, latencyReading(profile(-1L), health = null))
    }

    @Test
    fun `a hand-measured node shows that number until the sampler has one`() {
        // The manual "test latency" button writes Profile.latencyMs. Showing
        // "untested" next to a node the user just measured would be a lie.
        assertEquals(
            LatencyReading.Value(42L),
            latencyReading(profile(42L), health = null),
        )
    }

    @Test
    fun `the curve's value wins once there is one`() {
        // 100, then 0.2*200 + 0.8*100 = 120.
        val reading = latencyReading(profile(999L), health(100, 200))
        assertEquals(LatencyReading.Value(120L), reading)
    }

    @Test
    fun `a failed latest probe reads as a timeout, whatever came before`() {
        // The card reports "right now", and right now the node did not answer.
        assertEquals(LatencyReading.Timeout, latencyReading(profile(42L), health(100, 200, -1)))
    }

    @Test
    fun `a window of nothing but failures is a timeout, not a number`() {
        assertEquals(LatencyReading.Timeout, latencyReading(profile(-1L), health(-1, -1, -1)))
    }

    @Test
    fun `a still-collecting window still shows its number`() {
        // Only the sparkline is gated on the sample count; withholding the
        // number too would leave the card blank while the sampler worked.
        val reading = latencyReading(profile(-1L), health(80, 90))
        assertEquals(LatencyReading.Value(82L), reading)
    }
}
