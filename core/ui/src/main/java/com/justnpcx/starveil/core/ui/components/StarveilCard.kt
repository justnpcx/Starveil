package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilShapes
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * A glass card: the app's only grouping device.
 *
 * 68% white over the page's ambient gradient, a 1dp white edge, 22dp corners,
 * and the demo's own whisper of a shadow (7%, 4dp, 18dp blur) so the material
 * reads as lifted rather than drawn on. The edge is the highlight that makes
 * it glass; the shadow only separates it from the page.
 *
 * Cards do not nest. A card inside a card is two edges for one grouping, and
 * the inner one always looks like a mistake.
 */
@Composable
fun StarveilCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = StarveilShapes.card,
                ambientColor = StarveilShadow,
                spotColor = StarveilShadow,
            )
            .clip(StarveilShapes.card)
            .background(StarveilColors.surfaceGlass)
            .border(1.dp, StarveilColors.glassBorder, StarveilShapes.card),
        content = content,
    )
}

/**
 * The card's shadow, at the demo's 7% — a tint, not a drop shadow.
 *
 * Compose takes the shadow's colour per-instance rather than from the palette,
 * so it lives here next to the only thing that draws it.
 */
private val StarveilShadow = Color(0xFF1F2687).copy(alpha = 0.07f)

/**
 * A hairline inside a card, inset to the text column.
 *
 * [startInset] is the text's own left edge, so the rule starts where the words
 * do rather than at the card's border — a full-bleed line inside a rounded card
 * reads as a table.
 */
@Composable
fun StarveilDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = StarveilSpacing.lg,
    endInset: Dp = 0.dp,
) {
    val line = StarveilColors.hairline
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = line,
                    start = Offset(startInset.toPx(), stroke / 2),
                    end = Offset(size.width - endInset.toPx(), stroke / 2),
                    strokeWidth = stroke,
                )
            },
    )
}

/**
 * A row inside a card: 16dp of side padding, 14dp above and below.
 *
 * 14 rather than 16 so a two-line row (a title and its description) comes out
 * at 72dp — the height a list row needs to breathe without turning into a gap.
 */
@Composable
fun StarveilRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
