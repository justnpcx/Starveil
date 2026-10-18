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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * Pinch to zoom the time axis, drag to pan. Zoom is anchored on the pinch
 * centroid, so the sample under the fingers stays under them. The y-scale
 * stays global rather than per-window, so panning does not make the trace
 * jump vertically — zooming changes how much time is on screen, not how tall
 * the line looks.
 */
@Composable
fun TrafficSparkline(
    samples: List<Long>,
    /** The series' own colour; one chart carries one line. */
    color: Color,
    /**
     * Required: a canvas is invisible to a screen reader, and the shape is the
     * only thing here that the numbers above it do not say.
     */
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    state: SparklineState = rememberSparklineState(),
) {
    val total = samples.size

    // Global max, so the trace keeps its shape while panning.
    val maxValue = samples.maxOrNull() ?: 0L

    // The gesture must survive a growing history. Keying pointerInput on
    // `total` restarted the block — and so cancelled any in-flight drag — every
    // time a sample arrived (once a second, until the ring filled). Read the
    // live value instead and keep the block keyed on nothing.
    val latestTotal by rememberUpdatedState(total)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    state.onGesture(
                        gestureZoom = gestureZoom,
                        panXPx = pan.x,
                        centroidXPx = centroid.x,
                        total = latestTotal,
                        widthPx = size.width.toFloat(),
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            // No baseline: two charts side by side each drew their own, which
            // read as one divider with a hole in the middle (C2). A flat line
            // at zero is still drawn by the ribbon itself.
            if (maxValue <= 0L || total < 2) return@Canvas

            val window = visibleWindow(total, state.zoom, state.start)
            val scale = (size.height - 2f) / maxValue.toFloat()
            drawRibbon(samples, window, color, scale)
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
    internal fun onGesture(
        gestureZoom: Float,
        panXPx: Float,
        centroidXPx: Float,
        total: Int,
        widthPx: Float,
    ) {
        val next = applySparklineGesture(
            zoom = zoom,
            start = start,
            gestureZoom = gestureZoom,
            panXPx = panXPx,
            centroidXPx = centroidXPx,
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
 * pixel drag into a sample offset, keeping the sample under the fingers in
 * place during a zoom, and clamping at both ends of the history — live here
 * where they can be tested without a device.
 *
 * [centroidXPx] is the pinch focus in canvas pixels. Zooming keeps whatever
 * sample sits under it under it: pinch on the right-hand spike and the spike
 * stays put while the axis grows around it, rather than the window sliding
 * away from the finger.
 */
internal fun applySparklineGesture(
    zoom: Float,
    start: Float,
    gestureZoom: Float,
    panXPx: Float,
    centroidXPx: Float,
    total: Int,
    widthPx: Float,
): SparkViewport {
    val width = widthPx.coerceAtLeast(1f)
    val newZoom = (zoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)

    // How many samples the current zoom shows, and how many the new one will.
    // The counts do not depend on `start`.
    val shownCount = visibleWindow(total, zoom, start).count
    val nextCount = visibleWindow(total, newZoom, start).count

    // Where the fingers are, as a fraction of the window. Clamped, so a
    // centroid that lands outside the canvas (it can, mid-pinch) cannot throw
    // the window past either end.
    val anchor = (centroidXPx / width).coerceIn(0f, 1f)

    // The sample under the centroid is `start + anchor * shownCount`. Solve
    // `newStart + anchor * nextCount = that` for newStart.
    //
    // Deliberately the stored `start`, not the window's floored one: the
    // fraction lost by flooring is re-lost on every gesture event, and a pinch
    // delivers many, so the window slid a few samples off the fingers.
    val anchoredStart = start + anchor * (shownCount - nextCount)

    // Pixels -> samples, using the window that will actually be on screen.
    val perPixel = nextCount.toFloat() / width
    val maxStart = (total - nextCount).coerceAtLeast(0).toFloat()
    val newStart = (anchoredStart - panXPx * perPixel).coerceIn(0f, maxStart)
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

/**
 * One series, drawn as a smooth ribbon: a Catmull-Rom spline through the
 * samples, with a vertical fade under it.
 *
 * Catmull-Rom is used rather than quadratic smoothing because it passes
 * through every sample — a smoothed line that misses its own data is a
 * decoration, not a chart. Each segment's control points are the neighbours'
 * tangents, so the joins are continuous and the line has no corners.
 */
private fun DrawScope.drawRibbon(
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
    val points = ArrayList<Offset>(last - first + 1)
    for (index in first..last) {
        val x = (index - first) * stepX
        val y = (size.height - (samples[index] * scale)).coerceIn(0f, size.height - 1f)
        points.add(Offset(x, y))
    }

    val line = catmullRomPath(points)

    // The ribbon: the same curve closed to the baseline, fading out downwards.
    val fill = Path().apply {
        addPath(line)
        lineTo(points.last().x, size.height)
        lineTo(points.first().x, size.height)
        close()
    }
    drawPath(
        path = fill,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0.30f), Color.Transparent),
            startY = 0f,
            endY = size.height,
        ),
    )
    drawPath(
        path = line,
        color = color,
        style = Stroke(
            width = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 8f

/** Below this many samples a window is too short to read a shape from. */
private const val MIN_VISIBLE = 8
