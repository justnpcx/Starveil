package com.justnpcx.starveil.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
 * Material's switch is tinted from `primary`, and in this palette primary is
 * ink — so the platform widget would have come out black-on-black with a grey
 * thumb, and its 2dp outline is a shape nothing else in the app uses.
 *
 * The two states are told apart by more than colour: on is a solid ink track
 * with a white thumb inside a gold ring; off is a translucent track with a
 * plain white thumb at the other end. That survives being read in bright sun,
 * and it survives a colour-blind eye — which a tint change alone does not.
 */
@Composable
fun StarveilSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsStateSafe(
        target = when {
            !enabled -> StarveilColors.ink3.copy(alpha = 0.24f)
            checked -> StarveilColors.ink
            else -> StarveilColors.ink3.copy(alpha = 0.40f)
        },
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TRACK_WIDTH - THUMB_SIZE - 3.dp else 3.dp,
        animationSpec = StarveilMotion.stateTransition(),
        label = "switch-thumb",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = StarveilMotion.stateTransition(),
        label = "switch-ring",
    )

    Box(
        modifier = modifier
            // The switch is 28dp tall; the touch target is the platform's 48.
            .height(48.dp)
            .width(TRACK_WIDTH + 8.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(TRACK_WIDTH)
                .height(TRACK_HEIGHT)
                .clip(CircleShape)
                .background(trackColor),
        ) {
            val gold = StarveilColors.gold
            val surface = StarveilColors.surface
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(THUMB_SIZE)
                    .clip(CircleShape)
                    .background(surface)
                    .drawBehind {
                        // The ring fades in rather than appearing: a thumb that
                        // gains an outline in one frame reads as a glitch.
                        if (ringAlpha <= 0f) return@drawBehind
                        drawCircle(
                            color = gold.copy(alpha = ringAlpha),
                            radius = size.minDimension / 2 - 0.5.dp.toPx(),
                            center = Offset(size.width / 2, size.height / 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.dp.toPx(),
                            ),
                        )
                    },
            )
        }
    }
}

/**
 * `animateColorAsState`, with a colour that is already resolved.
 *
 * The token getters are composable reads, so they cannot be called inside the
 * lambda `animateColorAsState` takes — the value has to exist before this call.
 * Wrapping it keeps the call sites from each inventing their own workaround.
 */
@Composable
private fun animateColorAsStateSafe(target: Color) = androidx.compose.animation.animateColorAsState(
    targetValue = target,
    animationSpec = StarveilMotion.stateTransition(),
    label = "switch-track",
)