package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
 * one.
 */
@Composable
fun StarveilButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        color = if (!enabled) StarveilColors.surfaceGlass else if (secondary) Color.Transparent else StarveilColors.surfaceGlass,
        contentColor = if (enabled) StarveilColors.ink else StarveilColors.ink3,
        border = BorderStroke(
            1.dp,
            if (enabled) StarveilColors.glassBorder else StarveilColors.hairline,
        ),
    ) {
        Text(
            text = text,
            style = StarveilType.label,
            modifier = Modifier.padding(
                horizontal = StarveilSpacing.xl,
                vertical = StarveilSpacing.md,
            ),
        )
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
        color = if (enabled) StarveilColors.surfaceGlass else StarveilColors.surfaceGlass,
        contentColor = if (enabled) StarveilColors.ink else StarveilColors.ink3,
        border = BorderStroke(
            1.dp,
            if (enabled) StarveilColors.glassBorder else StarveilColors.hairline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = StarveilSpacing.xl,
                vertical = StarveilSpacing.md,
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
