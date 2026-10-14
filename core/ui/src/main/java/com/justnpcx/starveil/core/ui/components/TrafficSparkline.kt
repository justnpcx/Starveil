package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors

/**
 * A sparkline of recent traffic rates.
 *
 * Two series (down/up) drawn as filled-ish polylines over a shared y-scale, so
 * the relative shape is readable at a glance. Deliberately allocation-light:
 * the caller keeps a bounded history and passes it in.
 *
 * TODO (M3 D4, next step): pinch to zoom / pan the history. This is the
 * fixed-window version.
 */
@Composable
fun TrafficSparkline(
    downlinkSamples: List<Long>,
    uplinkSamples: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    val downColor = StarveilColors.GlowPrimary
    val upColor = StarveilColors.GlowSecondary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val maxValue = (downlinkSamples + uplinkSamples).maxOrNull() ?: 0L
            // Baseline grid so an all-zero stretch still reads as "flat", not broken.
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, size.height - 1f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height - 1f),
                strokeWidth = 1f,
            )
            if (maxValue <= 0L) return@Canvas

            val scale = (size.height - 2f) / maxValue.toFloat()
            drawSeries(downlinkSamples, downColor, scale)
            drawSeries(uplinkSamples, upColor, scale)
        }
    }
}

/** Maps samples onto the canvas; index-spaced, so the window fills the width. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    samples: List<Long>,
    color: Color,
    scale: Float,
) {
    if (samples.size < 2) return
    val stepX = size.width / (samples.size - 1).toFloat()
    val path = Path()
    samples.forEachIndexed { index, value ->
        val x = index * stepX
        val y = size.height - (value * scale).coerceIn(0f, size.height - 2f)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path = path, color = color, style = Stroke(width = 2f))
}
