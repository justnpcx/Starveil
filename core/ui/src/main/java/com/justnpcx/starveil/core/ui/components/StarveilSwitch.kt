package com.justnpcx.starveil.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilMotion

/** Track height, and therefore the thumb's diameter. */
private val TRACK_HEIGHT = 28.dp
private val TRACK_WIDTH = 48.dp
private val THUMB_SIZE = 22.dp

/**
 * A switch, drawn rather than borrowed.
 *
 * On is the accent — a solid track, because a mark this small does not need a
 * gradient — with a white thumb. Off is a ghost: a 40% ink3 track and an ink2
 * thumb. The two states differ in colour and in the thumb's position, which is
 * what lets the control be read at a glance without the platform's blue (or
 * green, on some ROMs) arriving with it.
 *
 * The accent track is one of a screen's three permitted appearances, so it is
 * the only filled control in the app: everything else is an outline.
 */
@Composable
fun StarveilSwitch(
    checked: Boolean,
    /** Null when the whole row is the target: the switch must not also be one. */
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TRACK_WIDTH - THUMB_SIZE - 3.dp else 3.dp,
        animationSpec = StarveilMotion.stateTransition(),
        label = "switch-thumb",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> StarveilColors.ink3
            checked -> Color.White
            else -> StarveilColors.ink2
        },
        animationSpec = StarveilMotion.stateTransition(),
        label = "switch-thumb-color",
    )
    val offTrack by animateColorAsState(
        targetValue = StarveilColors.ink3.copy(alpha = if (enabled) 0.40f else 0.20f),
        animationSpec = StarveilMotion.stateTransition(),
        label = "switch-track-off",
    )
    val onTrack by animateColorAsState(
        targetValue = if (enabled) StarveilColors.accent else StarveilColors.accent.copy(alpha = 0.40f),
        animationSpec = StarveilMotion.stateTransition(),
        label = "switch-track-on",
    )

    Box(
        modifier = modifier
            // The switch is 28dp tall; the touch target is the platform's 48.
            .height(48.dp)
            .width(TRACK_WIDTH + 8.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(TRACK_WIDTH)
                .height(TRACK_HEIGHT)
                .clip(CircleShape)
                .background(if (checked) onTrack else offTrack),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(THUMB_SIZE)
                    .clip(CircleShape)
                    .background(thumbColor)
                    .drawBehind {
                        // A hairline ring keeps the thumb off the track's edge
                        // when the track is nearly the same value as the page.
                        drawCircle(
                            color = Color.White.copy(alpha = 0.55f),
                            radius = size.minDimension / 2 - 0.5.dp.toPx(),
                            center = Offset(size.width / 2, size.height / 2),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    },
            )
        }
    }
}
