package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scene matching and the timing rules, as pure functions.
 *
 * These are the whole of the decision: the controller only feeds them the
 * current network and the clock.
 */
class SceneTest {

    private fun wifi(vararg ssids: String, id: Long = 1) = Scene(
        id = id,
        name = "wifi-$id",
        trigger = SceneTrigger.Wifi(ssids.toList()),
        preset = RoutePreset.GLOBAL_PROXY,
    )

    private fun mobile(id: Long = 2) = Scene(
        id = id,
        name = "mobile-$id",
        trigger = SceneTrigger.Mobile,
        preset = RoutePreset.GLOBAL_DIRECT,
    )

    private val home = NetworkState(NetworkTransport.WIFI, "Jim_5G")
    private val cellular = NetworkState(NetworkTransport.CELLULAR)

    @Test
    fun `a named network matches its ssid, case-insensitively`() {
        val scene = wifi("jim_5g")
        assertTrue(scene.trigger.matches(home))
        assertFalse(scene.trigger.matches(NetworkState(NetworkTransport.WIFI, "Other")))
    }

    @Test
    fun `an empty list means any wifi`() {
        // The degraded mode when location permission is denied: the name cannot
        // be read, but "this is Wi-Fi" still can.
        assertTrue(SceneTrigger.Wifi().matches(home))
        assertTrue(SceneTrigger.Wifi().matches(NetworkState(NetworkTransport.WIFI, null)))
    }

    @Test
    fun `a named network does not match when the name cannot be read`() {
        // The dangerous alternative: firing "any Wi-Fi" here would silently
        // turn a home-only scene into an everywhere scene.
        assertFalse(wifi("Jim_5G").trigger.matches(NetworkState(NetworkTransport.WIFI, null)))
    }

    @Test
    fun `mobile data does not match wifi and the reverse`() {
        assertFalse(mobile().trigger.matches(home))
        assertFalse(wifi("Jim_5G").trigger.matches(cellular))
        assertTrue(mobile().trigger.matches(cellular))
    }

    @Test
    fun `the first matching scene wins, in list order`() {
        val first = wifi("Jim_5G", id = 1).copy(preset = RoutePreset.BYPASS_MAINLAND)
        val second = wifi("Jim_5G", id = 2).copy(preset = RoutePreset.GLOBAL_DIRECT)

        assertEquals(1L, SceneMatcher.match(listOf(first, second), home)?.id)
        assertEquals(2L, SceneMatcher.match(listOf(second, first), home)?.id)
    }

    @Test
    fun `nothing matches is null, not an exception`() {
        assertNull(SceneMatcher.match(listOf(mobile()), home))
        assertNull(SceneMatcher.match(emptyList(), cellular))
    }

    @Test
    fun `scenes keep out of the way for thirty minutes after a manual choice`() {
        val now = 10_000_000L
        assertFalse(SceneTiming.allowsAutomatic(now, lastManualAtMs = now - 1))
        assertFalse(
            SceneTiming.allowsAutomatic(now, lastManualAtMs = now - SceneTiming.MANUAL_GRACE_MS + 1),
        )
        assertTrue(
            SceneTiming.allowsAutomatic(now, lastManualAtMs = now - SceneTiming.MANUAL_GRACE_MS),
        )
        // Never chosen by hand: nothing to wait for.
        assertTrue(SceneTiming.allowsAutomatic(now, lastManualAtMs = 0L))
    }
}
