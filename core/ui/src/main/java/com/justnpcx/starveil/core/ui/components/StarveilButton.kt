package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * A button: a glass capsule with an ink label.
 *
 * The accent never fills a button. It is spent on the ring and on one marker
 * per screen, and a filled button would spend the whole budget on the thing
 * that is easiest to draw.
 *
 * [secondary] is the same capsule with no fill, for the action beside a primary
 * one. [confirm] is a form's save: the selected fill (selBg) with selInk on it —
 * the language this design already uses for "the chosen one".
 *
 * The label is centred in both directions. Surface lays its content out from the
 * top-left, so a button stretched by a weight had its text pinned to the corner:
 * invisible at capsule width, obvious once the button was half a card wide.
 */
@Composable
fun StarveilButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    enabled: Boolean = true,
    /** A form's confirm action: selBg fill, selInk label. */
    confirm: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        color = when {
            !enabled -> StarveilColors.segBg
            confirm -> StarveilColors.selBg
            secondary -> Color.Transparent
            else -> StarveilColors.card
        },
        contentColor = when {
            !enabled -> StarveilColors.ink3
            confirm -> StarveilColors.selInk
            else -> StarveilColors.ink
        },
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> StarveilColors.hairline
                confirm -> Color.Transparent
                else -> StarveilColors.glassBorder
            },
        ),
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = StarveilSpacing.xl, vertical = StarveilSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = StarveilType.label)
        }
    }
}

/**
 * The same capsule, for a caller that needs to put something other than a
 * label inside it — a spinner while a connection is being made, or a title
 * with a subtitle under it.
 *
 * Exists so a screen never reaches for Material's filled button to get a
 * container: the accent does not fill buttons in this design.
 */
@Composable
fun StarveilButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        color = if (enabled) StarveilColors.card else StarveilColors.segBg,
        contentColor = if (enabled) StarveilColors.ink else StarveilColors.ink3,
        border = BorderStroke(
            1.dp,
            if (enabled) StarveilColors.glassBorder else StarveilColors.hairline,
        ),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(
                    horizontal = StarveilSpacing.xl,
                    vertical = StarveilSpacing.md,
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
