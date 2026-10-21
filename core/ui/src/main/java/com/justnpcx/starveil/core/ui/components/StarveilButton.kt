package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * A button: ink fill, page-coloured label.
 *
 * Gold is never a button. An accent that fills a control stops being an accent
 * the moment there are two buttons on a screen — and this design spends its
 * gold on the ring and on one badge, which is the whole budget.
 *
 * [secondary] is the same capsule outlined instead of filled, for the action
 * beside a primary one.
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
        color = when {
            !enabled -> StarveilColors.hairline
            secondary -> StarveilColors.bg
            else -> StarveilColors.ink
        },
        contentColor = when {
            !enabled -> StarveilColors.ink3
            secondary -> StarveilColors.ink
            else -> StarveilColors.bg
        },
        border = if (secondary) BorderStroke(1.dp, StarveilColors.ink) else null,
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
