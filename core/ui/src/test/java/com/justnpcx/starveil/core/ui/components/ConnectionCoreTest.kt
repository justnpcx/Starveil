package com.justnpcx.starveil.core.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §5.3's state-change rule, and the two ways a state can be wrong without the
 * compiler noticing: a warning that pulses like a healthy tunnel, and an
 * ambient loop that keeps running when it should be still.
 */
class ConnectionCoreMotionTest {

    @Test
    fun `a warning breathes slower and shallower than a healthy tunnel`() {
        val healthy = breathFor(ConnectionState.CONNECTED, active = true)!!
        val warning = breathFor(ConnectionState.WARNING, active = true)!!

        // Colour alone is not enough: at the healthy rate and amplitude an
        // amber ring still reads as "all good, in the wrong colour".
        assertTrue(
            "a warning must not pulse at the healthy rate (${warning.periodMs} vs ${healthy.periodMs})",
            warning.periodMs > healthy.periodMs,
        )
        assertTrue(
            "a warning must not pulse as hard as a healthy tunnel (${warning.peak} vs ${healthy.peak})",
            warning.peak < healthy.peak,
        )
    }

    @Test
    fun `the idle core breathes, more slowly and less than a live one`() {
        val healthy = breathFor(ConnectionState.CONNECTED, active = true)!!
        val idle = breathFor(ConnectionState.DISCONNECTED, active = true)!!

        // A static grey circle says "this is off". Idle is "ready when you
        // are", and it has to be visible as something other than a dead ring —
        // but it must not compete with the state that means traffic flows.
        assertTrue(
            "idle must breathe slower (${idle.periodMs} vs ${healthy.periodMs})",
            idle.periodMs > healthy.periodMs,
        )
        assertTrue(
            "idle must breathe shallower (${idle.peak} vs ${healthy.peak})",
            idle.peak < healthy.peak,
        )
    }

    @Test
    fun `connecting and blocked hold still`() {
        // Connecting rotates instead, and a block is a hard stop: neither
        // should look like something is ticking along.
        assertNull(breathFor(ConnectionState.CONNECTING, active = true))
        assertNull(breathFor(ConnectionState.BLOCKED, active = true))
    }

    @Test
    fun `nothing breathes while the screen is paused or motion is reduced`() {
        // §5.5: an ambient loop is decoration, and decoration must not burn
        // frames behind a paused screen — nor when the system asked for less
        // motion, where an infinite repeat collapses to a zero-length period
        // and becomes a busy loop.
        for (state in ConnectionState.entries) {
            assertNull(
                "expected $state to hold still when inactive",
                breathFor(state, active = false),
            )
        }
    }
}

/** The runtime half: every state has to draw, not just compile. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectionCoreRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `every state draws and the change between them is picked up`() {
        val state = mutableStateOf(ConnectionState.DISCONNECTED)
        rule.setContent {
            ConnectionCore(
                state = state.value,
                onClick = {},
                contentDescription = state.value.name,
            )
        }

        // Driven through one composition rather than one per state: the switch
        // is the thing that breaks, so it is the thing being exercised.
        for (candidate in ConnectionState.entries) {
            state.value = candidate
            rule.onNodeWithContentDescription(candidate.name).assertExists()
        }
    }
}
