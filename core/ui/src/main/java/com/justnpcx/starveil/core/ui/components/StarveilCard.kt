package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * The app has no cards.
 *
 * A section is a run of content separated from its neighbours by a hairline and
 * by air — not by a filled rectangle. That is the whole visual change: the old
 * screen was a stack of tinted blocks, and a stack of tinted blocks reads as a
 * form, not as a page. Removing the fill is what lets the type carry the
 * hierarchy instead of the boxes.
 *
 * This type is kept as the one place that knowledge lives, so "how do I group
 * these rows" has an answer that is not "draw a Card".
 *
 * @param divider which edge carries the hairline. A section that follows
 *   another gets a top rule; the first section on a page gets none, because the
 *   page's own margin already separates it from the title.
 * @param inset the rule starts where the text does, not at the screen edge.
 *   A full-bleed line under a 24dp margin reads as a table border.
 */
@Composable
fun StarveilSection(
    modifier: Modifier = Modifier,
    divider: SectionDivider = SectionDivider.Top,
    inset: Dp = StarveilSpacing.pageMargin,
    content: @Composable () -> Unit,
) {
    val line = StarveilColors.hairline
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (divider == SectionDivider.None) return@drawBehind
                val stroke = 1.dp.toPx()
                if (divider == SectionDivider.Top) {
                    drawLine(
                        color = line,
                        start = Offset(inset.toPx(), 0f),
                        end = Offset(size.width - inset.toPx(), 0f),
                        strokeWidth = stroke,
                    )
                } else {
                    drawLine(
                        color = line,
                        start = Offset(inset.toPx(), size.height - stroke / 2),
                        end = Offset(size.width - inset.toPx(), size.height - stroke / 2),
                        strokeWidth = stroke,
                    )
                }
            },
    ) {
        content()
    }
}

/** Which edge of a section carries its rule. */
enum class SectionDivider { None, Top, Bottom }

/**
 * A row inside a section: full width, its own hairline, page margin as padding.
 *
 * Rows are what a list of settings or nodes is made of. The rule sits on the
 * bottom edge and the caller decides whether the last row keeps it — a section
 * that ends the page usually does not need one, because the page ends.
 */
@Composable
fun StarveilRow(
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    content: @Composable () -> Unit,
) {
    StarveilSection(
        modifier = modifier,
        divider = if (showDivider) SectionDivider.Bottom else SectionDivider.None,
        inset = StarveilSpacing.pageMargin,
    ) {
        Box(modifier = Modifier.padding(vertical = StarveilSpacing.md)) {
            content()
        }
    }
}

/**
 * A 1dp rule, inset to the text column.
 *
 * For the places that are not a whole section: under a search field, between a
 * heading and its list.
 */
@Composable
fun StarveilRule(
    modifier: Modifier = Modifier,
    color: Color = StarveilColors.hairline,
    inset: Dp = StarveilSpacing.pageMargin,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = color,
                    start = Offset(inset.toPx(), stroke / 2),
                    end = Offset(size.width - inset.toPx(), stroke / 2),
                    strokeWidth = stroke,
                )
            },
    )
}

/**
 * The old name for a section, kept until the last screen is migrated.
 *
 * @deprecated use [StarveilSection]. It draws no fill and no border; a caller
 *   that relied on `selected` should draw its own selection cue, because the
 *   only permitted one is a wash and a leading rule on a list row.
 */
@Composable
@Deprecated("Use StarveilSection", ReplaceWith("StarveilSection"))
fun StarveilCard(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    StarveilSection(modifier = modifier, divider = SectionDivider.None, content = content)
}

/** The old card radius; only the dialogs use a radius now. */
val StarveilCardCorner = 16.dp
