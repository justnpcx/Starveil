package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.justnpcx.starveil.core.ui.theme.StarveilColors

/**
 * The page behind every page.
 *
 * A 160° wash from cool grey-blue through a green cast to warm sand, with three
 * very faint colour blooms over it — blue top-left, green right, amber bottom.
 * They are what makes a 68% white card read as glass: without something under
 * it, a translucent surface is just a lighter grey.
 *
 * This is painted once, behind the Scaffold, and every page is transparent over
 * it. Drawing it per screen would make each navigation re-rasterise a
 * full-screen gradient, and would put a seam at every page edge.
 */
@Composable
fun StarveilBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val top = StarveilColors.bgTop
    val mid = StarveilColors.bgMid
    val bottom = StarveilColors.bgBottom
    val washes = StarveilColors.washes
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(top, mid, bottom),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.35f, size.height),
                    ),
                )
                // The demo's three blooms, at the demo's positions and — more
                // importantly — the demo's shapes. They are wide, flat
                // ellipses (300×200, 280×220, 320×240 on a 390×844 phone), not
                // discs: a disc of the same radius floods the page.
                bloom(washes[0], x = 0.12f, y = 0.06f, rx = 0.385f, ry = 0.118f)
                bloom(washes[1], x = 0.88f, y = 0.30f, rx = 0.359f, ry = 0.130f)
                bloom(washes[2], x = 0.70f, y = 0.92f, rx = 0.410f, ry = 0.142f)
            },
        content = content,
    )
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
