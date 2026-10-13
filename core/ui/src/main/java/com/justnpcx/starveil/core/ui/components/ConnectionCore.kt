package com.justnpcx.starveil.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilMotion

/** Connection states. The core's visuals are a pure function of this state. */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    BLOCKED,
}

/**
 * ConnectionCore — the heart of the Dashboard.
 *
 * - [ConnectionState.CONNECTED]: cyan glow, slow breathing pulse.
 * - [ConnectionState.CONNECTING]: rotating charge arc.
 * - [ConnectionState.DISCONNECTED]: dim, static ring.
 * - [ConnectionState.BLOCKED]: red alert ring.
 *
 * Skeleton: visuals are placeholder-grade (Canvas strokes, no blur shader).
 * The state machine and the animation wiring are the real structure.
 */
@Composable
fun ConnectionCore(
    state: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "connection-core")

    // Breathing: only alive while connected.
    val breath by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = StarveilMotion.BREATH_PERIOD_MS / 2),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    // Charge rotation: only while connecting.
    val spin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = StarveilMotion.SPIN_PERIOD_MS),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    val ringColor by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED -> StarveilColors.GlowPrimary
            ConnectionState.CONNECTING -> StarveilColors.Connecting
            ConnectionState.DISCONNECTED -> StarveilColors.TextDim
            ConnectionState.BLOCKED -> StarveilColors.Blocked
        },
        label = "ring-color",
    )

    val breathScale = if (state == ConnectionState.CONNECTED) breath else 1f
    val rotation = if (state == ConnectionState.CONNECTING) spin else 0f

    Box(
        modifier = modifier
            .size(220.dp)
            .scale(breathScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            // Outer glow halo: stacked faint strokes (placeholder for a real blur).
            drawCircle(color = ringColor.copy(alpha = 0.08f), radius = size.minDimension / 2)
            drawCircle(
                color = ringColor.copy(alpha = 0.16f),
                radius = size.minDimension / 2 - 6.dp.toPx(),
                style = stroke,
            )
            // Main ring.
            drawCircle(color = ringColor, radius = size.minDimension / 2 - 18.dp.toPx(), style = stroke)
            // Charge arc while connecting.
            if (state == ConnectionState.CONNECTING) {
                drawArc(
                    color = Color.White,
                    startAngle = rotation - 90f,
                    sweepAngle = 100f,
                    useCenter = false,
                    style = stroke,
                )
            }
            // Core dot.
            drawCircle(
                color = ringColor,
                radius = if (state == ConnectionState.DISCONNECTED) 14.dp.toPx() else 22.dp.toPx(),
            )
        }
    }
}
