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
 * Outline only, in the accent's cyan, with the cyan for the text: this is one
 * of the three places a screen may spend the accent, and it is a line plus a
 * word rather than a filled pill. A filled badge would be a fourth kind of
 * block on a page that has none.
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
        color = StarveilColors.surfaceGlass,
        border = BorderStroke(1.dp, StarveilColors.accentA),
    ) {
        if (content != null) {
            content()
        } else {
            Text(
                text = text,
                style = StarveilType.tech,
                color = StarveilColors.accentA,
                modifier = Modifier.padding(
                    horizontal = StarveilSpacing.md,
                    vertical = StarveilSpacing.xs,
                ),
            )
        }
    }
}
