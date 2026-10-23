package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.justnpcx.starveil.core.ui.theme.StarveilType

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
    // Read before the Canvas: a colour token is a composable read, and DrawScope
    // is not a composable scope.
    val line = StarveilColors.accentA
    Box(modifier = modifier.height(height)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLatencyTrace(trace, line, strokeWidth = 1.5f)
        }
    }
}

/**
 * The node's health history (§7.2).
 *
 * Three things share one x-axis, which is the point of drawing them together:
 *
 * - the latency EMA trace, 1.5dp in the healthy colour;
 * - availability as a background bar, coloured by the window's band — jade
 *   above 99%, amber 95–99%, brick below 95%;
 * - packet loss as dots below the curve, so "slow" and "dropped" are
 *   distinguishable rather than both reading as a dip in one line.
 *
 * The fill under the curve is 8% of the line's own colour. The old chart faded
 * a saturated green across the whole area, which on a light page is a coloured
 * block — the one thing this design does not allow.
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
    /**
     * Null means "take the size the caller gave me" — the dashboard hands it a
     * weight so the chart absorbs the screen's remaining height (D4).
     */
    height: Dp? = 72.dp,
) {
    if (health.collecting) {
        CollectingState(modifier, height)
        return
    }

    val trace = emaSeries(health.samples)
    // Read before the Canvas: colour tokens are composable reads, and DrawScope
    // is not a composable scope.
    val line = StarveilColors.accentA
    val loss = StarveilColors.err
    val baseline = StarveilColors.hairline

    Box(
        modifier = (if (height != null) modifier.height(height) else modifier)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // No background plate and no band strip: the page is the only
            // background. The fill under the curve is the accent at 18%,
            // fading to nothing — shading, not a coloured block.
            drawLatencyFill(trace, line)

            // Loss first, so the trace sits over the dots rather than under
            // them — a dot the line crosses is still a dot you can see.
            drawLossDots(health.samples, loss)
            drawLatencyTrace(
                values = trace,
                color = line,
                strokeWidth = 1.5f,
            )

            // The baseline: the curve sits on a hairline, not in a box. It is
            // the only rule in the card and it stops the fill from floating.
            drawLine(
                color = baseline,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

/**
 * The ribbon under the latency trace: the same spline, closed to the baseline
 * and filled with a fade. It is what makes the curve sit on the card instead of
 * on a plate of its own colour.
 */
private fun DrawScope.drawLatencyFill(values: List<Long?>, color: Color) {
    val points = latencyTrace(values, size.width, size.height)
    if (points.size < 2) return
    val fill = Path().apply {
        addPath(catmullRomPath(points))
        lineTo(points.last().x, size.height)
        lineTo(points.first().x, size.height)
        close()
    }
    drawPath(
        path = fill,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0.18f), Color.Transparent),
            startY = 0f,
            endY = size.height,
        ),
    )
}

/**
 * "Collecting" — the honest alternative to a curve (§7.2).
 *
 * Says how much is missing rather than just "loading": the difference between
 * a node that has been watched for two minutes and one that has never
 * answered is exactly what the user wants to know, and a spinner would hide it.
 */
@Composable
private fun CollectingState(modifier: Modifier, height: Dp?) {
    // A skeleton in the card's own material: the glass face with a highlight
    // that sweeps across it, so the slot reads as "not yet" rather than as a
    // hole punched in the card.
    val base = StarveilColors.surfaceGlass
    val highlight = StarveilColors.ink.copy(alpha = 0.05f)
    val transition = rememberInfiniteTransition(label = "health-shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
        label = "health-shimmer-shift",
    )
    Box(
        modifier = (if (height != null) modifier.height(height) else modifier)
            .drawBehind {
                val band = size.width
                val start = shift * (size.width + band) - band
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(start, 0f),
                        end = Offset(start + band, 0f),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.core_health_collecting),
            style = StarveilType.label,
            color = StarveilColors.ink2,
        )
    }
}

/** The band's colour (§7.2): jade, amber, brick. Composable — tokens are reads. */
@Composable
private fun AvailabilityBand.color(): Color = when (this) {
    AvailabilityBand.GOOD -> StarveilColors.ok
    AvailabilityBand.FAIR -> StarveilColors.warn
    AvailabilityBand.POOR -> StarveilColors.err
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

    // The same spline the traffic ribbons use: the health curve had round
    // numbers but square corners, which is what made it look like a polyline
    // (C3). Round caps and joins keep the ends and the low points from
    // reading as cut-off.
    drawPath(
        path = catmullRomPath(points),
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun DrawScope.drawLossDots(samples: List<LatencySample>, color: Color) {
    val bottom = size.height - LOSS_DOT_RADIUS - 1f
    lossDots(samples, size.width).forEach { x ->
        drawCircle(color = color, radius = LOSS_DOT_RADIUS, center = Offset(x, bottom))
    }
}

/** Half a stroke, so the trace is not clipped at the top edge. */
private const val STROKE_INSET = 1f

/** Loss dot radius, in pixels. */
private const val LOSS_DOT_RADIUS = 2.5f

