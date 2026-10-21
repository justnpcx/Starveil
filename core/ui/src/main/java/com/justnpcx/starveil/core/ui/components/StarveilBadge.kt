package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.BorderStroke
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
 * A small outlined label: "auto-select", "in use".
 *
 * Outline only, in gold, with the deeper gold for the text — the one place the
 * accent is allowed to be small and still legible. A filled badge would be a
 * fourth kind of block on a page that has none.
 *
 * [content] is passed as a slot rather than a string so a caller can put an
 * icon before the word without this component growing a variant.
 */
@Composable
fun StarveilBadge(
    text: String,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = StarveilColors.bg,
        border = BorderStroke(1.dp, StarveilColors.gold),
    ) {
        if (content != null) {
            content()
        } else {
            Text(
                text = text,
                style = StarveilType.tech,
                color = StarveilColors.goldDeep,
                modifier = Modifier.padding(
                    horizontal = StarveilSpacing.sm,
                    vertical = StarveilSpacing.xs,
                ),
            )
        }
    }
}