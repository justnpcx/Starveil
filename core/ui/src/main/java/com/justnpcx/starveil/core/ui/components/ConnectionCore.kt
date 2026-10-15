package com.justnpcx.starveil.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
 * A state change moves four things at once — colour, breathing, core size and
 * glow — over 220ms, which is the design system's hard rule (§5.3): changing
 * only the colour reads as a glitch rather than a state change.
 */
@Composable
fun ConnectionCore(
    state: ConnectionState,
    onClick: () -> Unit,
    /** Required: this is the screen's primary control (design system §6.1). */
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // Ambient loops are decoration, and decoration must not burn frames behind
    // a paused or covered screen (§5.5). Gating the *spec* rather than the
    // value is what actually stops the animation.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val breathing = resumed && state == ConnectionState.CONNECTED
    val spinning = resumed && state == ConnectionState.CONNECTING

    // Driven by Animatable + a keyed LaunchedEffect rather than an
    // InfiniteTransition: a transition only accepts repeatable specs, so it
    // cannot be stopped, while cancelling the effect genuinely halts the loop
    // when the screen is not resumed (§5.5).
    val breath = remember { Animatable(1f) }
    LaunchedEffect(breathing) {
        if (!breathing) {
            breath.snapTo(1f)
            return@LaunchedEffect
        }
        breath.animateTo(
            targetValue = BREATH_MAX_SCALE,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = StarveilMotion.BREATH_PERIOD_MS / 2),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }

    val spin = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) {
            spin.snapTo(0f)
            return@LaunchedEffect
        }
        spin.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = StarveilMotion.SPIN_PERIOD_MS),
                repeatMode = RepeatMode.Restart,
            ),
        )
    }

    val ringColor by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED -> StarveilColors.GlowPrimary
            ConnectionState.CONNECTING -> StarveilColors.Connecting
            // Disconnected is its own colour, not the disabled-text one: an
            // idle tunnel is a normal state, not an unavailable control.
            ConnectionState.DISCONNECTED -> StarveilColors.Disconnected
            ConnectionState.BLOCKED -> StarveilColors.Blocked
        },
        animationSpec = StarveilMotion.stateTransition(),
        label = "ring-color",
    )

    // Drawn, not laid out — so this stays inside the "never animate layout
    // size" rule while still easing the change (§5.3).
    val coreRadius by animateDpAsState(
        targetValue = if (state == ConnectionState.DISCONNECTED) CORE_DOT_DIM else CORE_DOT_LIT,
        animationSpec = StarveilMotion.stateTransition(),
        label = "core-radius",
    )

    // Haptics ride the *transition*, not the press: the state change is what
    // the user is waiting on, and a press buzz on top of it would double up
    // (§5.4). Types map to the spec's table.
    val haptics = LocalHapticFeedback.current
    var previous by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        val from = previous
        previous = state
        if (from == state) return@LaunchedEffect
        when (state) {
            ConnectionState.CONNECTED -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            ConnectionState.DISCONNECTED -> haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
            ConnectionState.BLOCKED -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            ConnectionState.CONNECTING -> Unit
        }
    }

    val breathScale = if (state == ConnectionState.CONNECTED) breath.value else 1f
    val rotation = if (state == ConnectionState.CONNECTING) spin.value else 0f

    Box(
        modifier = modifier
            .size(CORE_SIZE)
            .scale(breathScale)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(CORE_SIZE)) {
            val stroke = Stroke(width = RING_STROKE.toPx(), cap = StrokeCap.Round)
            val outer = size.minDimension / 2

            // Glow, three layers of falling alpha. A blur shader would be one
            // pass, but stacked strokes cost nothing at this size and read the
            // same (§2.1 prescribes exactly this: 24% state glow, 16% halo,
            // then the solid ring).
            drawCircle(color = ringColor.copy(alpha = GLOW_STATE_ALPHA), radius = outer - 2.dp.toPx())
            drawCircle(
                color = ringColor.copy(alpha = GLOW_HALO_ALPHA),
                radius = outer - 8.dp.toPx(),
                style = stroke,
            )
            drawCircle(color = ringColor, radius = outer - 18.dp.toPx(), style = stroke)

            if (state == ConnectionState.CONNECTING) {
                // Brightened brand colour, never pure white (§2.1 forbids a
                // white glow).
                drawArc(
                    color = lerp(ringColor, Color.White, ARC_BRIGHTEN),
                    startAngle = rotation - 90f,
                    sweepAngle = 100f,
                    useCenter = false,
                    style = stroke,
                )
            }

            drawCircle(color = ringColor, radius = coreRadius.toPx())
        }
    }
}

private val CORE_SIZE = 220.dp
private val RING_STROKE = 6.dp
private val CORE_DOT_LIT = 22.dp
private val CORE_DOT_DIM = 14.dp

private const val BREATH_MAX_SCALE = 1.06f
private const val GLOW_STATE_ALPHA = 0.24f
private const val GLOW_HALO_ALPHA = 0.16f
private const val ARC_BRIGHTEN = 0.55f
