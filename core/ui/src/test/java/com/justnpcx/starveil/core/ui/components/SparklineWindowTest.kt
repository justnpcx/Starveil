package com.justnpcx.starveil.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The sparkline's windowing maths.
 *
 * The interesting part is the clamping: a window that runs off either end of
 * the history is exactly the bug this function exists to prevent, and it is
 * the only part of the component that can be checked without a device.
 */
class SparklineWindowTest {

    @Test
    fun `zoom 1 shows the whole history`() {
        val window = visibleWindow(total = 48, zoom = 1f, start = 0f)
        assertEquals(0, window.start)
        assertEquals(48, window.count)
    }

    @Test
    fun `zooming in narrows the window`() {
        val window = visibleWindow(total = 48, zoom = 2f, start = 0f)
        assertEquals(0, window.start)
        assertEquals(24, window.count)
    }

    @Test
    fun `panning past the end clamps to the last window`() {
        val window = visibleWindow(total = 48, zoom = 2f, start = 999f)
        assertEquals(24, window.start)
        assertEquals(48, window.endExclusive)
    }

    @Test
    fun `panning before the start clamps to zero`() {
        assertEquals(0, visibleWindow(total = 48, zoom = 2f, start = -50f).start)
    }

    @Test
    fun `zoom is clamped to the supported range`() {
        // Below 1x is just "everything"; above the maximum the window stops
        // shrinking, so it never becomes too short to read.
        assertEquals(48, visibleWindow(48, zoom = 0.1f, start = 0f).count)
        assertEquals(
            visibleWindow(48, zoom = 8f, start = 0f).count,
            visibleWindow(48, zoom = 1000f, start = 0f).count,
        )
    }

    @Test
    fun `a history shorter than the minimum window is still usable`() {
        val window = visibleWindow(total = 3, zoom = 8f, start = 0f)
        assertEquals(0, window.start)
        assertEquals(3, window.count)
    }

    @Test
    fun `an empty history yields an empty window`() {
        assertEquals(0, visibleWindow(total = 0, zoom = 1f, start = 0f).count)
    }
}

/**
 * What one gesture event does to the viewport.
 *
 * `detectTransformGestures` itself needs a real pointer stream, which is why
 * the arithmetic was pulled out here — the pixel-to-sample conversion, the
 * centroid anchoring and the two clamps are the parts that actually break.
 *
 * `centroidXPx = 0f` is the left edge, which is what the old, unanchored
 * implementation did for every zoom; the pan-only cases do not depend on it.
 */
class SparklineGestureTest {

    private val total = 48
    private val widthPx = 240f

    @Test
    fun `a pixel drag pans by the samples it covers`() {
        // Zooming to 2x shows 24 of 48 samples over 240px, so 10px is one
        // sample. Dragging 100px towards the past moves 10 samples forward.
        val viewport = applySparklineGesture(
            zoom = 1f,
            start = 0f,
            gestureZoom = 2f,
            panXPx = -100f,
            centroidXPx = 0f,
            total = total,
            widthPx = widthPx,
        )
        assertEquals(2f, viewport.zoom, 0.001f)
        assertEquals(10f, viewport.start, 0.001f)
    }

    @Test
    fun `dragging the other way moves back towards the start`() {
        val viewport = applySparklineGesture(
            zoom = 1f,
            start = 20f,
            gestureZoom = 1f,
            panXPx = 100f,
            centroidXPx = 0f,
            total = total,
            widthPx = widthPx,
        )
        // At 1x the whole history is shown, so there is nowhere to pan to.
        assertEquals(0f, viewport.start, 0.001f)
    }

    @Test
    fun `panning is clamped to the end of the history`() {
        val viewport = applySparklineGesture(
            zoom = 1f,
            start = 0f,
            gestureZoom = 2f,
            panXPx = -100_000f,
            centroidXPx = 0f,
            total = total,
            widthPx = widthPx,
        )
        // 24 samples on screen out of 48 leaves 24 as the furthest start.
        assertEquals(24f, viewport.start, 0.001f)
    }

    @Test
    fun `panning is clamped to the beginning of the history`() {
        val viewport = applySparklineGesture(
            zoom = 2f,
            start = 10f,
            gestureZoom = 1f,
            panXPx = 100_000f,
            centroidXPx = 0f,
            total = total,
            widthPx = widthPx,
        )
        assertEquals(0f, viewport.start, 0.001f)
    }

    @Test
    fun `zoom is clamped, so a wild pinch cannot escape the history`() {
        val pinchedIn = applySparklineGesture(
            1f, 0f, gestureZoom = 0.001f, panXPx = 0f, centroidXPx = 0f, total = total, widthPx = widthPx,
        )
        assertEquals(1f, pinchedIn.zoom, 0.001f)

        val pinchedOut = applySparklineGesture(
            1f, 0f, gestureZoom = 1000f, panXPx = 0f, centroidXPx = 0f, total = total, widthPx = widthPx,
        )
        assertEquals(8f, pinchedOut.zoom, 0.001f)
    }

    @Test
    fun `a zero-width canvas cannot produce a NaN offset`() {
        // The gesture can fire before the first layout, when the canvas has no
        // width yet; dividing by it would poison the viewport.
        val viewport = applySparklineGesture(
            zoom = 2f,
            start = 0f,
            gestureZoom = 1f,
            panXPx = -50f,
            centroidXPx = 0f,
            total = total,
            widthPx = 0f,
        )
        assertFalse(viewport.start.isNaN())
        assertFalse(viewport.zoom.isNaN())
    }

    @Test
    fun `zooming keeps the sample under the centroid in place`() {
        // The whole history (0..47) is on screen over 240px, so the sample at
        // the centre of the canvas is the middle one, 24.
        val viewport = applySparklineGesture(
            zoom = 1f,
            start = 0f,
            gestureZoom = 2f,
            panXPx = 0f,
            centroidXPx = widthPx / 2f,
            total = total,
            widthPx = widthPx,
        )

        // 2x shows 24 samples. Anchoring on the middle puts sample 24 at the
        // middle: start + 0.5 * 24 == 24.
        assertEquals(2f, viewport.zoom, 0.001f)
        assertEquals(12f, viewport.start, 0.001f)
    }

    @Test
    fun `anchoring at the left edge keeps the oldest sample`() {
        val viewport = applySparklineGesture(
            zoom = 1f,
            start = 0f,
            gestureZoom = 2f,
            panXPx = 0f,
            centroidXPx = 0f,
            total = total,
            widthPx = widthPx,
        )
        assertEquals(0f, viewport.start, 0.001f)
    }

    @Test
    fun `anchoring at the right edge keeps the newest sample`() {
        val viewport = applySparklineGesture(
            zoom = 1f,
            start = 0f,
            gestureZoom = 2f,
            panXPx = 0f,
            centroidXPx = widthPx,
            total = total,
            widthPx = widthPx,
        )
        // Window 24..47, so sample 47 is still against the right edge.
        assertEquals(24f, viewport.start, 0.001f)
    }

    @Test
    fun `a centroid outside the canvas cannot throw the window past an end`() {
        // A pinch can put the centroid off the canvas; the anchor is clamped,
        // not extrapolated.
        val beyondLeft = applySparklineGesture(
            1f, 0f, gestureZoom = 2f, panXPx = 0f, centroidXPx = -500f, total = total, widthPx = widthPx,
        )
        assertEquals(0f, beyondLeft.start, 0.001f)

        val beyondRight = applySparklineGesture(
            1f, 0f, gestureZoom = 2f, panXPx = 0f, centroidXPx = 500f, total = total, widthPx = widthPx,
        )
        assertEquals(24f, beyondRight.start, 0.001f)
    }

    @Test
    fun `zooming out also anchors on the centroid`() {
        // 4x shows 12 samples; window 18..29, so the middle sample is 24.
        val viewport = applySparklineGesture(
            zoom = 4f,
            start = 18f,
            gestureZoom = 0.5f,
            panXPx = 0f,
            centroidXPx = widthPx / 2f,
            total = total,
            widthPx = widthPx,
        )
        // 2x shows 24 samples; sample 24 must still be in the middle.
        assertEquals(2f, viewport.zoom, 0.001f)
        assertEquals(12f, viewport.start, 0.001f)
    }

    @Test
    fun `a pure pan does not depend on where the fingers are`() {
        val atLeft = applySparklineGesture(
            2f, 10f, gestureZoom = 1f, panXPx = -50f, centroidXPx = 0f, total = total, widthPx = widthPx,
        )
        val atRight = applySparklineGesture(
            2f, 10f, gestureZoom = 1f, panXPx = -50f, centroidXPx = widthPx, total = total, widthPx = widthPx,
        )
        assertEquals(atLeft.start, atRight.start, 0.001f)
    }

    @Test
    fun `anchoring at an edge keeps the edge sample when the window is panned`() {
        // Panned to 20 at 2x, so the sample against the left edge is 20 and the
        // one against the right edge is 43. Zooming from there must keep the
        // edge the fingers are on, not jump back to the ends of the history.
        val atLeft = applySparklineGesture(
            zoom = 2f, start = 20f, gestureZoom = 2f, panXPx = 0f, centroidXPx = 0f,
            total = total, widthPx = widthPx,
        )
        assertEquals(20f, atLeft.start, 0.001f)

        val atRight = applySparklineGesture(
            zoom = 2f, start = 20f, gestureZoom = 2f, panXPx = 0f, centroidXPx = widthPx,
            total = total, widthPx = widthPx,
        )
        // 4x shows 12 samples; keeping sample 43 against the right edge means
        // start 32, so `start + count == 44` still holds.
        assertEquals(32f, atRight.start, 0.001f)
    }

    @Test
    fun `a pinch delivered as many events does not drift off the anchor`() {
        // A real pinch arrives as a stream of small zoom steps, so the anchor
        // has to survive being applied repeatedly. Deriving it from the
        // window's floored start lost a fraction on every step and slid the
        // window several samples away from the fingers.
        var viewport = SparkViewport(zoom = 1f, start = 0f)
        val centroidXPx = widthPx / 4f
        repeat(20) {
            viewport = applySparklineGesture(
                zoom = viewport.zoom,
                start = viewport.start,
                gestureZoom = 1.05f,
                panXPx = 0f,
                centroidXPx = centroidXPx,
                total = total,
                widthPx = widthPx,
            )
        }

        // At 1x the sample under the centroid is 0.25 * 48 == 12; it must still
        // be under it, whatever the final zoom.
        val visible = visibleWindow(total, viewport.zoom, viewport.start).count
        val sampleUnderCentroid = viewport.start + 0.25f * visible
        assertEquals(12f, sampleUnderCentroid, 1f)
    }
}
