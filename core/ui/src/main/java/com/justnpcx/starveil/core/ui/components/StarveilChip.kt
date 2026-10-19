package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * Selected chip fill: how far the surface moves toward primary.
 *
 * The spec says "GlowPrimary 20%", which was written for a dark page; 24% is
 * where the same idea survives on a light one.
 */
private const val SELECTED_MIX = 0.24f

/** The "in use" marker on a chip. */
private val IN_USE_DOT = 6.dp

/** The platform's minimum touch target. */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * A filter chip: 16dp corners, tonal when idle, primary mix when selected.
 *
 * The radius is a fixed dp, not a percentage. A percent radius follows the
 * chip's own size, so a two-glyph label came out a circle and a long one a
 * capsule — the same component with two shapes in one row.
 *
 * [stateDescription] is passed in because the meaning of "selected" belongs to
 * the screen: a filtered group and a group the tunnel runs through look alike
 * and are not the same thing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StarveilChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    inUse: Boolean = false,
    stateDescription: String? = null,
) {
    // Named apart from the semantics property it feeds: inside the `semantics`
    // block an unqualified name would resolve to the receiver's, not the
    // parameter's.
    val isSelected = selected
    val description = stateDescription
    Surface(
        modifier = modifier
            // Material's 48dp minimum applies to its own components, not to a
            // raw Surface; the chip's padding alone came out around 40dp.
            .heightIn(min = MIN_TOUCH_TARGET)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
            )
            .semantics {
                this.selected = isSelected
                if (description != null) this.stateDescription = description
            },
        shape = RoundedCornerShape(StarveilCardCorner),
        // Theme colours, not the HUD's dark constants: in dark mode primary is
        // the glow and surfaceVariant is Raised, so this is the spec's "20%"
        // exactly; in light mode the same two tokens keep the row on the page.
        // Mixed rather than alpha-composited, because a 20% wash of a dark
        // primary over a light page moved the fill by nine levels out of 255.
        color = if (selected) {
            lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary,
                SELECTED_MIX,
            )
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        // The accent lives on the border, not the label: primary text on a 24%
        // primary fill measured 3.55:1 in the light scheme, and a 12sp label
        // has to clear 4.5.
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            null
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = StarveilSpacing.md,
                vertical = StarveilSpacing.sm,
            ),
        ) {
            if (inUse) {
                // A dot rather than another chip colour: "selected" here means
                // "the list is filtered to this group", which is a different
                // thing from "the tunnel goes through it".
                Box(
                    modifier = Modifier
                        .size(IN_USE_DOT)
                        .clip(CircleShape)
                        .background(StarveilColors.Connected),
                )
                Spacer(modifier = Modifier.width(StarveilSpacing.xs))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
