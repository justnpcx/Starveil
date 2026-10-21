package com.justnpcx.starveil.core.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The ring's contract, as something a test can hold on to.
 *
 * What the pulse used to break is the thing worth protecting: the ring must
 * never change size, because everything below it is positioned from its box.
 *
 * [ConnectionCoreGeometry] is the part worth testing: it is the one piece of
 * the drawing other blocks depend on, and it is a pure function.
 */
class ConnectionCoreGeometryTest {

    @Test
    fun `the ring is the same size in every state`() {
        // This is the whole point of dropping the pulse. The rates, the node
        // card and the health card are all measured from this box; a state
        // change that resized it would move every one of them.
        val sizes = ConnectionState.entries.map { ConnectionCoreGeometry.sizeFor(it) }
        assertEquals(
            "the ring's box must not depend on the state",
            1,
            sizes.distinct().size,
        )
    }

    @Test
    fun `the ring's stroke is 4dp`() {
        // The spec's weight. A stroke that scaled with the diameter would make
        // a larger ring a different weight of line.
        assertEquals(4f, ConnectionCoreGeometry.strokeDp)
    }

    @Test
    fun `the ring is 240dp on every screen`() {
        // One size. The design is measured from this box, so a ring that grows
        // with the viewport makes the layout different on every device.
        assertEquals(240f, ConnectionCoreGeometry.diameterFor(availableHeightDp = 900f))
        assertEquals(240f, ConnectionCoreGeometry.diameterFor(availableHeightDp = 700f))
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
                label = state.value.name,
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
