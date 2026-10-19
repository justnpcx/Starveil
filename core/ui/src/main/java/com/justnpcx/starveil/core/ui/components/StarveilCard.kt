package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors

/**
 * The one card radius in the app.
 *
 * Cards used to be drawn by each screen, so the corner, the fill and the
 * outline drifted apart. A card is a tonal surface with this radius; nothing
 * else.
 */
val StarveilCardCorner = 16.dp

/** How far a selected card's fill moves toward the glow. */
private const val SELECTED_HALO_ALPHA = 0.16f

/** The outline a selected card carries; idle cards have none. */
private val SELECTED_BORDER_WIDTH = 2.dp

/**
 * A card: `surfaceVariant`, 16dp, no outline unless selected.
 *
 * The fill says "this is a surface". Selection adds a halo and a glow border,
 * never a different fill, so the card's colour keeps one meaning.
 *
 * The click handling stays with the caller: a card is sometimes tappable,
 * sometimes long-pressable, and sometimes neither.
 */
@Composable
fun StarveilCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(StarveilCardCorner),
        color = if (selected) {
            lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                StarveilColors.GlowPrimary,
                SELECTED_HALO_ALPHA,
            )
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (selected) {
            BorderStroke(SELECTED_BORDER_WIDTH, StarveilColors.BorderGlow)
        } else {
            null
        },
        content = content,
    )
}
