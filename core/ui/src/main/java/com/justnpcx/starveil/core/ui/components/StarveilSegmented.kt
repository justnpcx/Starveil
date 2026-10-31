package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * A two-or-more segment control: a trough, and one selected pill.
 *
 * Both directions are always tappable — including the direction that is already
 * selected, which is what makes the control impossible to get stuck in. The
 * caller owns the state; this component only reports the tap.
 */
@Composable
fun StarveilSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    proportional: Boolean = false,
) {
    // Equal thirds cut a long label: the part that does not fit is painted
    // over by the next pill, and no ellipsis ever appears because the text is
    // not what is being truncated — the space is. With [proportional] the
    // width follows the words, so "Follow system" keeps its second word while
    // "Day" gives up room it never needed.
    val weights = options.map { if (proportional) (it.length + 4).toFloat() else 1f }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(StarveilColors.segBg)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(weights[index])
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (selected) StarveilColors.selBg else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = StarveilType.body.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (selected) StarveilColors.selInk else StarveilColors.ink2,
                    maxLines = 1,
                )
            }
        }
    }
}
