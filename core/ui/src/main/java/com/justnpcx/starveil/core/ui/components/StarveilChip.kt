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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/** The "in use" marker on a chip. */
private val IN_USE_DOT = 6.dp

/** The platform's minimum touch target. */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * What a chip is sitting on.
 *
 * The page is translucent material with a white highlight edge; a dialog is an
 * opaque surface with no highlight to speak of. A chip drawn for the page
 * looks washed out in a dialog, which is why Material's own chip defaults —
 * a lavender container and an outline — used to leak through there.
 */
enum class ChipSurface { Page, Dialog }

/**
 * A filter chip: a capsule, its edge and label blue when selected.
 *
 * Selection is the edge, not a fill — except on a dialog, where the fill is
 * what separates a selected chip from the surface behind it.
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
    enabled: Boolean = true,
    surface: ChipSurface = ChipSurface.Page,
) {
    // Named apart from the semantics property it feeds: inside the `semantics`
    // block an unqualified name would resolve to the receiver's, not the
    // parameter's.
    val isSelected = selected
    val description = stateDescription

    val fill: Color = when {
        !enabled -> StarveilColors.surfaceGlass
        surface == ChipSurface.Dialog && selected -> StarveilColors.accentWash
        surface == ChipSurface.Dialog -> StarveilColors.bg
        else -> StarveilColors.surfaceGlass
    }
    val edge: Color = when {
        !enabled -> StarveilColors.hairline
        selected -> StarveilColors.accent
        surface == ChipSurface.Dialog -> StarveilColors.ink3
        else -> StarveilColors.glassBorder
    }
    val content: Color = when {
        !enabled -> StarveilColors.ink3
        selected -> StarveilColors.accentText
        else -> StarveilColors.ink2
    }

    Surface(
        modifier = modifier
            // Material's 48dp minimum applies to its own components, not to a
            // raw Surface; the chip's padding alone came out around 40dp.
            .heightIn(min = MIN_TOUCH_TARGET)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
            )
            .semantics {
                this.selected = isSelected
                if (description != null) this.stateDescription = description
            },
        shape = CircleShape,
        color = fill,
        border = BorderStroke(1.dp, edge),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = StarveilSpacing.lg,
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
                        .background(StarveilColors.ok),
                )
                Spacer(modifier = Modifier.width(StarveilSpacing.xs))
            }
            Text(
                text = label,
                style = StarveilType.label,
                color = content,
            )
        }
    }
}
