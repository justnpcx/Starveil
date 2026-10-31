package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors

/**
 * A switch, on Material's own.
 *
 * The platform's switch is the control people already know: 52x32, a 16dp thumb
 * when off and a 24dp one with a tick when on, a 2dp outline, and the press and
 * disabled states that come with it. Drawing our own was the thing that made
 * this app's settings look like a different app's settings.
 *
 * Only the colours are ours, and they are the task book's: the off state is a
 * quiet grey trough with a grey thumb and outline, the on state is the accent
 * with a thumb that stays legible on it — white in the day, deep blue at night,
 * so the tick is light on dark rather than a white disc on a pale blue track.
 */
@Composable
fun StarveilSwitch(
    checked: Boolean,
    /** Null when the whole row is the target: the switch must not also be one. */
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = StarveilIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = StarveilColors.swOnTh,
            checkedTrackColor = StarveilColors.accent,
            // A border on a filled track is a second edge for nothing.
            checkedBorderColor = Color.Transparent,
            checkedIconColor = StarveilColors.swChk,
            uncheckedThumbColor = StarveilColors.swOut,
            uncheckedTrackColor = StarveilColors.swOff,
            uncheckedBorderColor = StarveilColors.swOut,
            uncheckedIconColor = Color.Transparent,
        ),
    )
}
