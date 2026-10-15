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
        rule.onNodeWithTag(TAG).performTouchInput { swipeLeft() }

        assertTrue("start should move after a pan, was ${state.start}", state.start > 0f)
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
        val expectedFull = 300f * samplesPerPx
        assertTrue(
            "start=${state.start} should reflect the whole drag (~$expectedFull samples)",
            state.start > expectedFull * 0.6f,
        )
    }

    private companion object {
        const val TAG = "sparkline"
    }
}

/** Two fingers apart from the centre: a zoom-in, past touch slop. */
private fun androidx.compose.ui.test.TouchInjectionScope.pinchOut() {
    pinch(
        start0 = center + Offset(-20f, 0f),
        end0 = center + Offset(-160f, 0f),
        start1 = center + Offset(20f, 0f),
        end1 = center + Offset(160f, 0f),
    )
}
