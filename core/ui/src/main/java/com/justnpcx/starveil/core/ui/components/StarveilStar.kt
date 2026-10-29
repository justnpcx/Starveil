package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilBrand
import com.justnpcx.starveil.core.ui.theme.StarveilColors

/**
 * The four-point star: the brand's one shape.
 *
 * Filled it carries the 135-degree brand gradient; hollow it is a 1.5dp ring
 * in [StarveilColors.ringOff]. The silhouette is the same in both modes and at
 * every size, so the 13dp list mark, the 30dp ring mark and the launcher icon
 * are recognisably one thing.
 */
@Composable
fun StarveilStar(
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    size: Dp = 30.dp,
) {
    val off = StarveilColors.ringOff
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val path = Path().apply {
            moveTo(c.x, c.y - r)
            cubicTo(c.x + 0.08f * r, c.y - 0.30f * r, c.x + 0.30f * r, c.y - 0.08f * r, c.x + r, c.y)
            cubicTo(c.x + 0.30f * r, c.y + 0.08f * r, c.x + 0.08f * r, c.y + 0.30f * r, c.x, c.y + r)
            cubicTo(c.x - 0.08f * r, c.y + 0.30f * r, c.x - 0.30f * r, c.y + 0.08f * r, c.x - r, c.y)
            cubicTo(c.x - 0.30f * r, c.y - 0.08f * r, c.x - 0.08f * r, c.y - 0.30f * r, c.x, c.y - r)
            close()
        }
        if (filled) {
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = StarveilBrand.colors,
                    start = Offset(0f, 0f),
                    end = Offset(this.size.width, this.size.height),
                ),
            )
        } else {
            drawPath(
                path = path,
                color = off,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

/** The ring's own fill, for a solid star without a gradient. */
@Composable
fun StarveilStar(color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val path = Path().apply {
            moveTo(c.x, c.y - r)
            cubicTo(c.x + 0.08f * r, c.y - 0.30f * r, c.x + 0.30f * r, c.y - 0.08f * r, c.x + r, c.y)
            cubicTo(c.x + 0.30f * r, c.y + 0.08f * r, c.x + 0.08f * r, c.y + 0.30f * r, c.x, c.y + r)
            cubicTo(c.x - 0.08f * r, c.y + 0.30f * r, c.x - 0.30f * r, c.y + 0.08f * r, c.x - r, c.y)
            cubicTo(c.x - 0.30f * r, c.y - 0.08f * r, c.x - 0.08f * r, c.y - 0.30f * r, c.x, c.y - r)
            close()
        }
        drawPath(path = path, color = color)
    }
}
