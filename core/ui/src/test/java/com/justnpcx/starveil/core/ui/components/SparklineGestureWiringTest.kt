package com.justnpcx.starveil.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sparkline's gesture WIRING — the layer the pure-function tests cannot
 * reach.
 *
 * [SparklineWindowTest] pins the arithmetic but never proves that Compose
 * actually feeds a gesture into it: a wrong argument slot, a stale capture, or
 * a `pointerInput` key that restarts mid-drag would all leave those tests
 * green. These run on the JVM under Robolectric, which injects real pointer
 * events, so the whole path (pointer stream -> detectTransformGestures ->
 * applySparklineGesture -> state) is exercised without a device.
 *
 * This exists because the project's only device is a vivo ROM that refuses to
 * start an instrumentation host Activity, so the previous attempt at an
 * on-device gesture test had to be scrapped.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SparklineGestureWiringTest {

    @get:Rule
    val rule = createComposeRule()

    private val samples = List(48) { it.toLong() * 1_000 }

    @Test
    fun `a pinch zooms the window in`() {
        val state = SparklineState()
        rule.setContent {
            TrafficSparkline(
                downlinkSamples = samples,
                uplinkSamples = samples,
                modifier = Modifier.testTag(TAG),
                state = state,
            )
        }

        rule.onNodeWithTag(TAG).performTouchInput { pinchOut() }

        assertTrue("zoom should increase, was ${state.zoom}", state.zoom > 1f)
    }

    @Test
    fun `a drag pans the window once it is zoomed in`() {
        val state = SparklineState()
        rule.setContent {
            TrafficSparkline(
                downlinkSamples = samples,
                uplinkSamples = samples,
                modifier = Modifier.testTag(TAG),
                state = state,
            )
        }

        rule.onNodeWithTag(TAG).performTouchInput { pinchOut() }
        assertTrue("precondition: zoomed in, was ${state.zoom}", state.zoom > 1f)

        // swipeLeft drags towards the past, which moves the window forward.
        // Measured as a delta: the pinch itself already anchored the window
        // somewhere non-zero.
        val before = state.start
        rule.onNodeWithTag(TAG).performTouchInput { swipeLeft() }

        assertTrue("start should move after a pan: $before -> ${state.start}", state.start > before)
    }

    /**
     * The regression this test was written for: `pointerInput` used to be keyed
     * on the sample count, so a sample arriving mid-drag restarted the block
     * and cancelled the gesture — the drag simply stopped responding.
     *
     * The history grows once a second until it fills, so this was reachable in
     * normal use, not just in theory.
     *
     * The gesture is deliberately split across two injection calls: a single
     * `performTouchInput` block does not recompose mid-gesture, so changing the
     * sample count inside it would not move the key at all and the test would
     * pass either way. Measured against the old keyed-on-`total` code, this
     * test reports half the expected pan.
     */
    @Test
    fun `a drag that spans a new sample still applies the whole pan`() {
        val state = SparklineState()
        var count by mutableIntStateOf(48)
        rule.setContent {
            TrafficSparkline(
                downlinkSamples = List(count) { it.toLong() },
                uplinkSamples = List(count) { it.toLong() },
                modifier = Modifier.testTag(TAG),
                state = state,
            )
        }

        rule.onNodeWithTag(TAG).performTouchInput { pinchOut() }
        assertTrue("precondition: zoomed in, was ${state.zoom}", state.zoom > 1f)

        val widthPx = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width.toFloat()
        val windowCount = visibleWindow(count, state.zoom, state.start).count
        val samplesPerPx = windowCount / widthPx

        val before = state.start
        rule.onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(-150f, 0f))
        }

        // A new sample arrives mid-drag, and the frame it triggers recomposes
        // the composable — which is what used to cancel the gesture.
        count = 49
        rule.waitForIdle()

        rule.onNodeWithTag(TAG).performTouchInput {
            moveBy(Offset(-150f, 0f))
            up()
        }

        // ~300px of drag. Losing the second half leaves about half the samples.
        // A delta, because the pinch above already anchored the window.
        val expectedFull = 300f * samplesPerPx
        val moved = state.start - before
        assertTrue(
            "the drag moved $moved samples, expected ~$expectedFull",
            moved > expectedFull * 0.6f,
        )
    }

    @Test
    fun `a pinch on the right half keeps the later samples under the fingers`() {
        assertPinchAnchor(0.75f)
    }

    @Test
    fun `a pinch on the left half keeps the earlier samples under the fingers`() {
        assertPinchAnchor(0.25f)
    }

    /**
     * A pinch focused near an edge, where the anchor is close to 0 or 1 and
     * the anchored window is closest to the ends of the history. The middle
     * fractions are covered above; these pin down that the anchoring does not
     * behave differently at the ends — in particular that it neither clamps
     * into the wrong window nor slides off the finger.
     */
    @Test
    fun `a pinch next to the right edge still zooms and still anchors there`() {
        assertPinchAnchor(0.9f)
    }

    @Test
    fun `a pinch next to the left edge still zooms and still anchors there`() {
        assertPinchAnchor(0.1f)
    }

    private companion object {
        const val TAG = "sparkline"
    }

    /**
     * Pinching at [fraction] across the chart must leave the window anchored
     * there: the sample under the fingers stays under the fingers.
     *
     * The whole history is on screen at 1x, so the sample under the centroid is
     * `fraction * total`; after zooming to `n` visible samples it must still be
     * at that fraction, i.e. `start == fraction * (total - n)`. The old,
     * unanchored zoom ignored the centroid and always left `start` at 0, so
     * this fails against it.
     *
     * The zoom is asserted first: at 1x the expected start is 0 whatever the
     * fraction, so a gesture that failed to register would otherwise satisfy
     * the anchor assertion trivially.
     */
    private fun assertPinchAnchor(fraction: Float) {
        val state = SparklineState()
        rule.setContent {
            TrafficSparkline(
                downlinkSamples = samples,
                uplinkSamples = samples,
                modifier = Modifier.testTag(TAG),
                state = state,
            )
        }

        val widthPx = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width.toFloat()
        rule.onNodeWithTag(TAG).performTouchInput { pinchOutAt(fraction, widthPx) }

        assertTrue(
            "the pinch did not zoom at all (zoom=${state.zoom}); the anchor check would be vacuous",
            state.zoom > 1f,
        )

        val visible = visibleWindow(samples.size, state.zoom, 0f).count
        val expected = fraction * (samples.size - visible)
        assertEquals("anchored at $fraction", expected, state.start, 1.5f)
    }
}

/**
 * A zoom-in centred on [fraction] of the node's width.
 *
 * The spread shrinks near the edges and stops just short of them. A pointer
 * that runs past the canvas *is* still reported once the gesture is captured
 * (verified: pushing a finger 40px outside changes nothing), so this is not
 * load-bearing — it just avoids the test depending on whether a pointer
 * exactly on the boundary counts as inside, which is the kind of detail that
 * can shift between Compose versions.
 */
private fun androidx.compose.ui.test.TouchInjectionScope.pinchOutAt(fraction: Float, widthPx: Float) {
    val focusX = widthPx * fraction
    val room = (minOf(focusX, widthPx - focusX) - EDGE_MARGIN_PX).coerceAtLeast(1f)
    val spread = minOf(room, MAX_SPREAD_PX)
    pinch(
        start0 = Offset(focusX - spread / 3f, center.y),
        end0 = Offset(focusX - spread, center.y),
        start1 = Offset(focusX + spread / 3f, center.y),
        end1 = Offset(focusX + spread, center.y),
    )
}

/** Keeps a pinch finger just inside the canvas boundary. */
private const val EDGE_MARGIN_PX = 8f

/** Otherwise a pinch at the middle would reach far wider than it needs to. */
private const val MAX_SPREAD_PX = 60f

/** A zoom-in from the middle of the node. */
private fun androidx.compose.ui.test.TouchInjectionScope.pinchOut() {
    pinch(
        start0 = center + Offset(-20f, 0f),
        end0 = center + Offset(-160f, 0f),
        start1 = center + Offset(20f, 0f),
        end1 = center + Offset(160f, 0f),
    )
}
