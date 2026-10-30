package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.justnpcx.starveil.core.ui.theme.StarveilColors

/**
 * The page behind every page.
 *
 * A 160° wash from cool grey-blue through a green cast to warm sand, with three
 * very faint colour blooms over it — blue top-left, green right, amber bottom.
 * They are what makes a 68% white card read as glass: without something under
 * it, a translucent surface is just a lighter grey.
 *
 * Painted once, behind the Scaffold; every page is transparent over it.
 *
 * **Baked, not re-shaded.** Four full-screen gradients (one linear, three
 * radial) are cheap on paper and ruinous on a Fire TV's fill rate: measured
 * 97% janky frames on page transitions, against 7% for the same build with a
 * flat page. So the gradients are rendered into a bitmap once per size and
 * blitted afterwards — the result is identical, the cost is one texture copy
 * instead of four shader passes over two million pixels.
 */
@Composable
fun StarveilBackground(
    modifier: Modifier = Modifier,
    /**
     * Whether the page carries the three blooms.
     *
     * Only the home page is: it is where the connection card sits on
     * something. On every other page the blooms landed under inputs and behind
     * cards and read as stains, so those pages are a flat [StarveilColors.bg].
     */
    showWash: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val top = StarveilColors.bgTop
    val mid = StarveilColors.bgMid
    val bottom = StarveilColors.bgBottom
    val washes = StarveilColors.washes
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                // Keyed on size: the cache block re-runs when the page resizes
                // and not once per frame.
                val baked = bakeBackground(
                    size = size,
                    top = top,
                    mid = mid,
                    bottom = bottom,
                    washes = if (showWash) washes else emptyList(),
                )
                onDrawBehind { drawImage(baked) }
            },
        content = content,
    )
}


/** Renders the whole page background into one bitmap. */
private fun bakeBackground(
    size: Size,
    top: Color,
    mid: Color,
    bottom: Color,
    washes: List<Color>,
): ImageBitmap {
    val width = size.width.toInt().coerceAtLeast(1)
    val height = size.height.toInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(bitmap),
        size = Size(width.toFloat(), height.toFloat()),
    ) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(top, mid, bottom),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.35f, size.height),
            ),
        )
        if (washes.size < 3) return@draw
        // The demo's three blooms, at the demo's positions and — more
        // importantly — the demo's shapes. They are wide, flat ellipses
        // (300×200, 280×220, 320×240 on a 390×844 phone), not discs: a disc of
        // the same radius floods the page.
        bloom(washes[0], x = 0.12f, y = 0.06f, rx = 0.385f, ry = 0.118f)
        bloom(washes[1], x = 0.88f, y = 0.30f, rx = 0.359f, ry = 0.130f)
        bloom(washes[2], x = 0.70f, y = 0.92f, rx = 0.410f, ry = 0.142f)
    }
    return bitmap
}

/**
 * One colour bloom: a soft ellipse that fades out by 65% of its radius.
 *
 * [rx] and [ry] are fractions of the page's width and height. Compose only
 * draws circular radial gradients, so the vertical squash is a transform on
 * the whole draw rather than a property of the brush.
 */
private fun DrawScope.bloom(
    color: Color,
    x: Float,
    y: Float,
    rx: Float,
    ry: Float,
) {
    val center = Offset(size.width * x, size.height * y)
    val radius = size.width * rx
    val squash = (size.height * ry) / radius
    withTransform({
        scale(1f, squash, pivot = center)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to color, 0.65f to Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}
