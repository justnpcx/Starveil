package com.justnpcx.starveil.feature.dashboard

import com.justnpcx.starveil.feature.dashboard.TrafficWatch.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one question the kernel's state cannot answer: is anything going through?
 *
 * Every case here is a clock rather than a wait — the window is ten seconds,
 * and a test that slept through it would be both slow and flaky.
 */
class TrafficWatchTest {

    private val watch = TrafficWatch()

    @Test
    fun `a fresh watch says nothing`() {
        assertEquals(Verdict.Waiting, watch.check(1_000))
        assertFalse(watch.isArmed)
    }

    @Test
    fun `inside the window it is still waiting`() {
        watch.arm(0)

        assertEquals(Verdict.Waiting, watch.check(TrafficWatch.WINDOW_MS - 1))
        assertTrue(watch.isArmed)
    }

    @Test
    fun `at the deadline with nothing proxied it is silent`() {
        watch.arm(0)

        assertEquals(Verdict.Silent, watch.check(TrafficWatch.WINDOW_MS))
        // And it stays silent: the answer does not change back by itself.
        assertEquals(Verdict.Silent, watch.check(TrafficWatch.WINDOW_MS * 5))
        assertFalse(watch.isArmed)
    }

    @Test
    fun `a proxied connection settles it before the deadline`() {
        watch.arm(0)

        assertEquals(Verdict.Carrying, watch.onProxied(1_500))
        // No longer armed, so the countdown cannot later call it silent.
        assertFalse(watch.isArmed)
        assertEquals(Verdict.Carrying, watch.check(TrafficWatch.WINDOW_MS * 10))
    }

    @Test
    fun `a connection after the window does not undo the verdict`() {
        // The tunnel was silent, and something finally went through a minute
        // later. That is a fact worth knowing, but the state is already set and
        // flipping it back would hide the thing the user needs to see.
        watch.arm(0)
        assertEquals(Verdict.Silent, watch.check(TrafficWatch.WINDOW_MS))

        assertEquals(Verdict.Silent, watch.onProxied(TrafficWatch.WINDOW_MS * 6))
    }

    @Test
    fun `only the first proxied connection counts`() {
        watch.arm(0)

        assertEquals(Verdict.Carrying, watch.onProxied(1_000))
        assertEquals(Verdict.Carrying, watch.onProxied(2_000))
        assertFalse(watch.isArmed)
    }

    @Test
    fun `arming again starts a new question`() {
        watch.arm(0)
        watch.check(TrafficWatch.WINDOW_MS)
        assertEquals(Verdict.Silent, watch.check(TrafficWatch.WINDOW_MS))

        watch.arm(TrafficWatch.WINDOW_MS * 2)

        assertEquals(Verdict.Waiting, watch.check(TrafficWatch.WINDOW_MS * 2 + 1))
        assertTrue(watch.isArmed)
    }

    @Test
    fun `reset forgets everything`() {
        watch.arm(0)
        watch.onProxied(500)

        watch.reset()

        assertEquals(Verdict.Waiting, watch.check(TrafficWatch.WINDOW_MS * 10))
        assertFalse(watch.isArmed)
    }

    @Test
    fun `a proxied connection before arming is ignored`() {
        // The kernel reports connections from whatever was running before this
        // screen existed; they say nothing about this attempt.
        assertEquals(Verdict.Waiting, watch.onProxied(1_000))
        assertFalse(watch.isArmed)
    }
}