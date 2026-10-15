package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors

/**
 * A sparkline of recent traffic rates.
 *
 * Two series (down/up) drawn as polylines over a shared y-scale, so the
 * relative shape is readable at a glance. Deliberately allocation-light: the
 * caller keeps a bounded history and passes it in.
 *
 * Pinch to zoom the time axis, drag to pan. The y-scale stays global rather
 * than per-window, so panning does not make the trace jump vertically —
 * zooming changes how much time is on screen, not how tall the line looks.
 */
@Composable
fun TrafficSparkline(
    downlinkSamples: List<Long>,
    uplinkSamples: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    state: SparklineState = rememberSparklineState(),
) {
    val total = maxOf(downlinkSamples.size, uplinkSamples.size)

    // Global max, so the trace keeps its shape while panning.
    val maxValue = (downlinkSamples + uplinkSamples).maxOrNull() ?: 0L

    // The gesture must survive a growing history. Keying pointerInput on
    // `total` restarted the block — and so cancelled any in-flight drag — every
    // time a sample arrived (once a second, until the ring filled). Read the
    // live value instead and keep the block keyed on nothing.
    val latestTotal by rememberUpdatedState(total)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    state.onGesture(
                        gestureZoom = gestureZoom,
                        panXPx = pan.x,
                        total = latestTotal,
                        widthPx = size.width.toFloat(),
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            // Baseline grid so an all-zero stretch still reads as "flat", not
            // broken.
            drawLine(
                color = StarveilColors.ChartGrid,
                start = Offset(0f, size.height - 1f),
                end = Offset(size.width, size.height - 1f),
                strokeWidth = 1f,
            )
            if (maxValue <= 0L || total < 2) return@Canvas

            val window = visibleWindow(total, state.zoom, state.start)
            val scale = (size.height - 2f) / maxValue.toFloat()
            drawSeries(downlinkSamples, window, StarveilColors.Chart1, scale)
            drawSeries(uplinkSamples, window, StarveilColors.Chart2, scale)
        }
    }
}

/**
 * The sparkline's viewport: how far in it is zoomed, and the first sample it
 * shows.
 *
 * Hoisted out of the composable so it can be driven and asserted directly —
 * the gesture *plumbing* (a real pointer stream reaching [onGesture]) is the
 * one part the pure-function tests cannot reach.
 */
@Stable
class SparklineState internal constructor() {

    internal var zoom by mutableFloatStateOf(MIN_ZOOM)
    internal var start by mutableFloatStateOf(0f)

    /** Fold one gesture event into the viewport. */
    internal fun onGesture(gestureZoom: Float, panXPx: Float, total: Int, widthPx: Float) {
        val next = applySparklineGesture(
            zoom = zoom,
            start = start,
            gestureZoom = gestureZoom,
            panXPx = panXPx,
            total = total,
            widthPx = widthPx,
        )
        zoom = next.zoom
        start = next.start
    }
}

@Composable
fun rememberSparklineState(): SparklineState = remember { SparklineState() }

/**
 * The viewport after one gesture event.
 *
 * Pure on purpose. `detectTransformGestures` needs a real pointer stream, so
 * the parts of the gesture that are actually easy to get wrong — turning a
 * pixel drag into a sample offset, and clamping at both ends of the history —
 * live here where they can be tested without a device.
 */
internal fun applySparklineGesture(
    zoom: Float,
    start: Float,
    gestureZoom: Float,
    panXPx: Float,
    total: Int,
    widthPx: Float,
): SparkViewport {
    val newZoom = (zoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val window = visibleWindow(total, newZoom, start)
    // Pixels -> samples, using the window that will actually be on screen.
    val perPixel = window.count.toFloat() / widthPx.coerceAtLeast(1f)
    val maxStart = (total - window.count).coerceAtLeast(0).toFloat()
    val newStart = (start - panXPx * perPixel).coerceIn(0f, maxStart)
    return SparkViewport(newZoom, newStart)
}

/** A zoom level and the first sample it shows. */
internal data class SparkViewport(val zoom: Float, val start: Float)

/**
 * The slice of a [total]-sample history that is on screen: at [zoom] 1 that is
 * everything, and higher zoom shows a [start]-anchored window of it.
 *
 * Pure and clamped, because the interesting part is the clamping — a window
 * that runs past either end of the history is the bug this exists to prevent.
 */
internal fun visibleWindow(total: Int, zoom: Float, start: Float): SparkWindow {
    if (total <= 0) return SparkWindow(0, 0)
    val safeZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    val count = (total / safeZoom).toInt().coerceIn(minOf(MIN_VISIBLE, total), total)
    val first = start.toInt().coerceIn(0, total - count)
    return SparkWindow(first, count)
}

/** A window into the sample history: [count] samples starting at [start]. */
internal data class SparkWindow(val start: Int, val count: Int) {
    val endExclusive: Int get() = start + count
}

/** Maps the windowed samples onto the canvas; index-spaced, so it fills the width. */
private fun DrawScope.drawSeries(
    samples: List<Long>,
    window: SparkWindow,
    color: Color,
    scale: Float,
) {
    if (window.count < 2 || samples.isEmpty()) return
    val first = window.start.coerceAtMost(samples.lastIndex)
    val last = (window.endExclusive - 1).coerceAtMost(samples.lastIndex)
    if (last - first < 1) return

    val stepX = size.width / (last - first).toFloat()
    val path = Path()
    for (index in first..last) {
        val x = (index - first) * stepX
        val y = size.height - (samples[index] * scale).coerceIn(0f, size.height - 2f)
        if (index == first) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path = path, color = color, style = Stroke(width = 2f))
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 8f

/** Below this many samples a window is too short to read a shape from. */
private const val MIN_VISIBLE = 8
