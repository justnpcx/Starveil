package com.justnpcx.starveil.core.ui.components

import com.justnpcx.starveil.core.model.LatencySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart's geometry, which is the part that can be wrong in a way that is
 * hard to see: an axis that does not line up, a scale that flatters the node,
 * a trace drawn off the canvas.
 */
class NodeHealthChartTest {

    private val delta = 0.01f

    private fun samples(vararg latencies: Long): List<LatencySample> =
        latencies.mapIndexed { index, ms -> LatencySample(atMs = index * 1_000L, latencyMs = ms) }

    @Test
    fun `nothing to draw without two points`() {
        assertTrue(latencyTrace(emptyList(), width = 100f, height = 50f).isEmpty())
        assertTrue(latencyTrace(listOf(10L), width = 100f, height = 50f).isEmpty())
        assertTrue(latencyTrace(listOf(null, null), width = 100f, height = 50f).isEmpty())
    }

    @Test
    fun `the trace spans the full width`() {
        val points = latencyTrace(listOf(100L, 200L), width = 100f, height = 50f)
        assertEquals(2, points.size)
        assertEquals(0f, points.first().x, delta)
        assertEquals(100f, points.last().x, delta)
    }

    @Test
    fun `the y axis starts at zero, so a fast node looks fast`() {
        // Rescaling to the window's minimum would draw a 100 ms and a 200 ms
        // node with the same shape. Zero-anchored, the slower one is visibly
        // higher up.
        val points = latencyTrace(listOf(100L, 200L), width = 100f, height = 50f)
        assertEquals(25f, points[0].y, delta)
        assertEquals(1f, points[1].y, delta)
    }

    @Test
    fun `a leading gap is skipped but keeps its place on the axis`() {
        // Two failures then two successes: the trace starts at index 1's x, not
        // at the left edge — the line starts where the data starts.
        val points = latencyTrace(listOf(null, 100L, 200L), width = 100f, height = 50f)
        assertEquals(2, points.size)
        assertEquals(50f, points[0].x, delta)
        assertEquals(100f, points[1].x, delta)
    }

    @Test
    fun `the trace stays inside the canvas when the bar is reserved`() {
        val points = latencyTrace(
            values = listOf(50L, 500L),
            width = 100f,
            height = 50f,
            bottomInset = 4f,
        )
        assertTrue("top point was clipped: ${points.last()}", points.last().y >= 0f)
        assertTrue("bottom point ran into the bar: ${points.first()}", points.first().y <= 46f)
    }

    @Test
    fun `loss dots land on the same x as the trace`() {
        // They share an axis, which is the whole reason both are index-spaced
        // over the full sample list.
        val list = samples(10, -1, 20, -1)
        val dots = lossDots(list, width = 90f)
        assertEquals(listOf(30f, 90f), dots)

        val trace = latencyTrace(
            values = listOf(10L, 10L, 20L, 20L),
            width = 90f,
            height = 50f,
        )
        assertEquals(dots[0], trace[1].x, delta)
        assertEquals(dots[1], trace[3].x, delta)
    }

    @Test
    fun `only failures produce a loss dot`() {
        assertTrue(lossDots(samples(10, 20, 30), width = 90f).isEmpty())
        assertEquals(2, lossDots(samples(-1, 20, -1), width = 90f).size)
        // A single sample has no axis to place a dot on.
        assertTrue(lossDots(samples(-1), width = 90f).isEmpty())
    }
}
