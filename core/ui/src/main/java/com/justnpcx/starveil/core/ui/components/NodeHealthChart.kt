package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.model.AvailabilityBand
import com.justnpcx.starveil.core.model.LatencySample
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.emaSeries
import com.justnpcx.starveil.core.ui.R
import com.justnpcx.starveil.core.ui.theme.StarveilColors

/**
 * The latency mini-curve on a node card (§6.3).
 *
 * No axes, no dots, no labels — at this size the only readable thing is the
 * shape, and anything else would be noise. The card's own latency number
 * carries the value and the status dot carries the verdict.
 *
 * Draws nothing below [NodeHealth.MIN_SAMPLES]: a two-point line is not a
 * shape, and a card that drew one would be claiming more than it knows.
 */
@Composable
fun LatencySparkline(
    health: NodeHealth,
    // Width comes from the caller: the card gives it a fixed slot beside the
    // latency number, and a fillMaxWidth() baked in here would override it.
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: Dp = 20.dp,
) {
    val trace = if (health.collecting) emptyList() else emaSeries(health.samples)
    Box(modifier = modifier.height(height)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLatencyTrace(trace, StarveilColors.Chart3, strokeWidth = 1.5f)
        }
    }
}

/**
 * The node's health history (§7.2).
 *
 * Three things share one x-axis, which is the point of drawing them together:
 *
 * - the latency EMA trace, in [StarveilColors.Chart3];
 * - availability as a background bar, coloured by the window's band — green
 *   above 99%, amber 95–99%, red below 95%;
 * - packet loss as red dots below the curve, so "slow" and "dropped" are
 *   distinguishable rather than both reading as a dip in one line.
 *
 * Below [NodeHealth.MIN_SAMPLES] it says so instead of drawing. A curve through
 * three points is a claim about a shape that has not happened yet.
 */
@Composable
fun NodeHealthChart(
    health: NodeHealth,
    /**
     * Required: the curve is a canvas, and its shape — how low it got, how
     * high, how often it dropped — is not in the availability number beside it.
     */
    contentDescription: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: Dp = 72.dp,
) {
    if (health.collecting) {
        CollectingState(health, modifier, height)
        return
    }

    val band = health.band.color()
    val trace = emaSeries(health.samples)

    Box(
        modifier = modifier
            .height(height)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // The background bar. Tinted across the plot so the band reads at a
            // glance, and solid along the bottom edge so it is unambiguous
            // rather than a shade the eye has to judge.
            drawRect(color = band.copy(alpha = BAND_TINT_ALPHA))
            drawRect(
                color = band,
                topLeft = Offset(0f, size.height - BAND_BAR_HEIGHT_PX),
                size = androidx.compose.ui.geometry.Size(size.width, BAND_BAR_HEIGHT_PX),
            )

            // Loss first, so the trace sits over the dots rather than under
            // them — a dot the line crosses is still a dot you can see.
            drawLossDots(health.samples, StarveilColors.Chart5)
            drawLatencyTrace(
                values = trace,
                color = StarveilColors.Chart3,
                strokeWidth = 2f,
                // Keep the trace clear of the availability bar.
                bottomInset = BAND_BAR_HEIGHT_PX + 1f,
            )
        }
    }
}

/**
 * "Collecting" — the honest alternative to a curve (§7.2).
 *
 * Says how much is missing rather than just "loading": the difference between
 * a node that has been watched for two minutes and one that has never
 * answered is exactly what the user wants to know, and a spinner would hide it.
 */
@Composable
private fun CollectingState(health: NodeHealth, modifier: Modifier, height: Dp) {
    Box(
        modifier = modifier
            .height(height)
            .background(StarveilColors.Raised),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                R.string.core_health_collecting,
                health.samples.size,
                NodeHealth.MIN_SAMPLES,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = StarveilColors.TextDisabled,
        )
    }
}

/** The band's colour (§7.2): green, amber, red. */
private fun AvailabilityBand.color(): Color = when (this) {
    AvailabilityBand.GOOD -> StarveilColors.Connected
    AvailabilityBand.FAIR -> StarveilColors.Warning
    AvailabilityBand.POOR -> StarveilColors.Blocked
}

/**
 * The EMA trace as canvas points.
 *
 * x is index-spaced across the whole sample list, so the trace and the loss
 * dots share an axis and a sample at index i lands at the same x in both. That
 * also means the trace fills the width from the fifth sample on rather than
 * creeping in from the left — the same convention the traffic sparkline uses.
 *
 * y starts at zero rather than at the window's minimum. A latency curve is read
 * as "how slow", and rescaling to the observed range would draw a 40 ms node
 * and a 400 ms node with the same shape, which is the opposite of informative.
 *
 * Leading nulls (before the first success) are skipped, but their x positions
 * are not reclaimed — the trace starts where the data starts.
 */
internal fun latencyTrace(
    values: List<Long?>,
    width: Float,
    height: Float,
    bottomInset: Float = 0f,
): List<Offset> {
    if (values.size < 2) return emptyList()
    val max = values.filterNotNull().maxOrNull()?.coerceAtLeast(1L) ?: return emptyList()

    val stepX = width / (values.size - 1)
    val top = STROKE_INSET
    val bottom = (height - bottomInset - STROKE_INSET).coerceAtLeast(top + 1f)
    val span = bottom - top

    return values.mapIndexedNotNull { index, value ->
        value ?: return@mapIndexedNotNull null
        Offset(index * stepX, bottom - (value.toFloat() / max) * span)
    }
}

/** x positions of the failed samples, for the loss dots (§7.2). */
internal fun lossDots(samples: List<LatencySample>, width: Float): List<Float> {
    if (samples.size < 2) return emptyList()
    val stepX = width / (samples.size - 1)
    return samples.mapIndexedNotNull { index, sample ->
        if (sample.reachable) null else index * stepX
    }
}

private fun DrawScope.drawLatencyTrace(
    values: List<Long?>,
    color: Color,
    strokeWidth: Float,
    bottomInset: Float = 0f,
) {
    val points = latencyTrace(values, size.width, size.height, bottomInset)
    if (points.size < 2) return

    val path = Path()
    points.forEachIndexed { index, point ->
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawLossDots(samples: List<LatencySample>, color: Color) {
    val bottom = size.height - BAND_BAR_HEIGHT_PX - LOSS_DOT_RADIUS - 1f
    lossDots(samples, size.width).forEach { x ->
        drawCircle(color = color, radius = LOSS_DOT_RADIUS, center = Offset(x, bottom))
    }
}

/** Half a stroke, so the trace is not clipped at the top edge. */
private const val STROKE_INSET = 1f

/** How tall the availability bar is, in pixels. */
private const val BAND_BAR_HEIGHT_PX = 4f

/** Loss dot radius, in pixels. */
private const val LOSS_DOT_RADIUS = 2.5f

/** The band tint across the plot: present, but not so strong it colours the trace. */
private const val BAND_TINT_ALPHA = 0.14f
