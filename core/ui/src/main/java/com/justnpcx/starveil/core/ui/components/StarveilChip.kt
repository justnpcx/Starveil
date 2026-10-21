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
 * A filter chip: a capsule, outlined when idle, ink-filled when selected.
 *
 * Inverted rather than tinted. A selected chip is the darkest thing in the row
 * — ink fill, page-coloured label — which is the only selection cue in this
 * design that cannot be confused with a status colour. A tinted fill would put
 * a second colour into a row whose job is to be quiet.
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
        color = if (selected) StarveilColors.ink else StarveilColors.bg,
        // The outline is what makes an idle chip a chip rather than a word; a
        // selected one has no outline because its fill already says so.
        border = if (selected) null else BorderStroke(1.dp, StarveilColors.hairline),
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
                        .background(if (selected) StarveilColors.bg else StarveilColors.ok),
                )
                Spacer(modifier = Modifier.width(StarveilSpacing.xs))
            }
            Text(
                text = label,
                style = StarveilType.label,
                color = if (selected) StarveilColors.bg else StarveilColors.ink2,
            )
        }
    }
}