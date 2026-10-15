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
 * the arithmetic was pulled out here — the pixel-to-sample conversion and the
 * two clamps are the parts that actually break.
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
            total = total,
            widthPx = widthPx,
        )
        assertEquals(0f, viewport.start, 0.001f)
    }

    @Test
    fun `zoom is clamped, so a wild pinch cannot escape the history`() {
        val pinchedIn = applySparklineGesture(1f, 0f, gestureZoom = 0.001f, panXPx = 0f, total = total, widthPx = widthPx)
        assertEquals(1f, pinchedIn.zoom, 0.001f)

        val pinchedOut = applySparklineGesture(1f, 0f, gestureZoom = 1000f, panXPx = 0f, total = total, widthPx = widthPx)
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
            total = total,
            widthPx = 0f,
        )
        assertFalse(viewport.start.isNaN())
        assertFalse(viewport.zoom.isNaN())
    }
}
