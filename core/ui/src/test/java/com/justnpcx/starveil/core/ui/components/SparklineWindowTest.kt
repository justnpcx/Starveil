package com.justnpcx.starveil.core.ui.components

import org.junit.Assert.assertEquals
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
