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
 * A filter chip: a glass capsule, its edge and label blue when selected.
 *
 * Selection is the edge, not a fill. A filled chip would be a second solid
 * block in a row of outlines, and the accent is only allowed to be a line here.
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
        shape = CircleShape,
        color = StarveilColors.surfaceGlass,
        border = BorderStroke(
            1.dp,
            if (selected) StarveilColors.accent else StarveilColors.glassBorder,
        ),
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
                color = if (selected) StarveilColors.accentText else StarveilColors.ink2,
            )
        }
    }
}
