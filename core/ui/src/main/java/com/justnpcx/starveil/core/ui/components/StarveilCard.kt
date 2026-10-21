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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilShapes
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * A glass card: the app's only grouping device.
 *
 * One step off black, a 1dp edge, 20dp corners, and no shadow. The edge is what
 * gives it a shape; a shadow on a black page is invisible work, and a heavier
 * fill would read as a grey block — which is exactly the look this replaces.
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
            .clip(StarveilShapes.card)
            .background(StarveilColors.surfaceGlass)
            .border(1.dp, StarveilColors.glassBorder, StarveilShapes.card),
        content = content,
    )
}

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
