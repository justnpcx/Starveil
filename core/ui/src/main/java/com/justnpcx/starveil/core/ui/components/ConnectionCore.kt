package com.justnpcx.starveil.core.ui.components

import android.provider.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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

    /**
     * Up, but not trustworthy — the tunnel is carrying traffic and something
     * about it is wrong (a leak the self-check found). Distinct from
     * [BLOCKED], which is traffic not leaving at all.
     */
    WARNING,
    BLOCKED,
}

/**
 * ConnectionCore — the heart of the Dashboard.
 *
 * - [ConnectionState.CONNECTED]: cyan glow, slow breathing pulse.
 * - [ConnectionState.WARNING]: amber, and a *slower, shallower* pulse — a
 *   warning that breathes at the healthy rate reads as "all good" in the
 *   wrong colour.
 * - [ConnectionState.CONNECTING]: rotating charge arc.
 * - [ConnectionState.DISCONNECTED]: dim, static ring.
 * - [ConnectionState.BLOCKED]: red alert ring.
 *
 * A state change moves four things at once — colour, breathing, core size and
 * glow — over 220ms, which is the design system's hard rule (§5.3): changing
 * only the colour reads as a glitch rather than a state change. The breathing
 * is therefore *eased* back to rest rather than snapped; an instant jump is
 * the "瞬变" the same rule forbids.
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

    val animationsEnabled = rememberAnimationsEnabled()
    val breathSpec = breathFor(state, active = resumed && animationsEnabled)
    val spinning = resumed && animationsEnabled && state == ConnectionState.CONNECTING

    // Driven by Animatable + a keyed LaunchedEffect rather than an
    // InfiniteTransition: a transition only accepts repeatable specs, so it
    // cannot be stopped, while cancelling the effect genuinely halts the loop
    // when the screen is not resumed (§5.5).
    val breath = remember { Animatable(REST_SCALE) }
    LaunchedEffect(breathSpec) {
        val spec = breathSpec
        if (spec == null) {
            // Ease back to rest rather than snapping. The scale is on screen
            // the whole time, so an instant jump to 1f is exactly the "瞬变"
            // §5.3 forbids.
            breath.animateTo(REST_SCALE, StarveilMotion.stateTransition())
            return@LaunchedEffect
        }
        breath.animateTo(
            targetValue = spec.peak,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = spec.periodMs / 2),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }

    val spin = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) {
            // Unlike the breath, this one may snap: the charge arc is drawn
            // only while CONNECTING, so the rotation value is off screen the
            // moment the state changes.
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
            ConnectionState.WARNING -> StarveilColors.Warning
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
            // The haptics table (§5.4) puts "断线 / 被阻断" in one "long buzz,
            // warning feel" bucket. A warning is that same bucket — it is the
            // one thing the user must not miss — but it is not a rejection, so
            // it gets the long buzz rather than the reject pattern.
            ConnectionState.WARNING -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            ConnectionState.CONNECTING -> Unit
        }
    }

    // Always the animated value, never a `if (state == …) 1f` guard: the guard
    // would throw the scale back to 1f the instant the state changed, which is
    // the snap this is supposed to avoid. When the core stops breathing the
    // effect above eases this to REST_SCALE instead.
    val breathScale = breath.value
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
            val ringRadius = outer - RING_INSET.toPx()
            val spread = GLOW_SPREAD.toPx()

            // The glow is a radial gradient, not stacked strokes: the falloff
            // is continuous rather than banded, it is one draw call, and it
            // needs no blur pass — a RenderEffect would cost GPU work every
            // frame while the core breathes, and only exists from API 31
            // anyway (minSdk is 29). The stops peak at the ring and fade both
            // ways, so it reads as light coming off the ring rather than a
            // filled disc (the design's structure is strokes + ring + dot).
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        ((ringRadius - spread) / outer).coerceIn(0f, 1f) to Color.Transparent,
                        (ringRadius / outer).coerceIn(0f, 1f) to ringColor.copy(alpha = GLOW_PEAK_ALPHA),
                        ((ringRadius + spread) / outer).coerceIn(0f, 1f) to Color.Transparent,
                    ),
                    center = center,
                    radius = outer,
                ),
                radius = outer,
            )

            // Main ring.
            drawCircle(color = ringColor, radius = ringRadius, style = stroke)

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

            // A small bloom under the dot, so it reads as lit rather than
            // pasted on.
            val dotRadius = coreRadius.toPx()
            val dotGlow = dotRadius * DOT_GLOW_SCALE
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to ringColor.copy(alpha = DOT_GLOW_ALPHA),
                        1f to Color.Transparent,
                    ),
                    center = center,
                    radius = dotGlow,
                ),
                radius = dotGlow,
            )
            drawCircle(color = ringColor, radius = dotRadius)
        }
    }
}

private val CORE_SIZE = 220.dp
private val RING_STROKE = 6.dp
private val RING_INSET = 22.dp
private val CORE_DOT_LIT = 22.dp
private val CORE_DOT_DIM = 14.dp

/** How far the ring's glow reaches either side of it. */
private val GLOW_SPREAD = 20.dp

/** Scale at rest: the core's size when nothing is pulsing it. */
private const val REST_SCALE = 1f

private const val BREATH_MAX_SCALE = 1.06f

/** Shallower than [BREATH_MAX_SCALE] on purpose — see [StarveilMotion.WARNING_BREATH_PERIOD_MS]. */
private const val WARNING_BREATH_MAX_SCALE = 1.03f

private const val GLOW_PEAK_ALPHA = 0.5f
private const val DOT_GLOW_SCALE = 2.2f
private const val DOT_GLOW_ALPHA = 0.35f
private const val ARC_BRIGHTEN = 0.55f

/**
 * How the core breathes in a given state, or null for a ring that holds still.
 *
 * A pair rather than one amplitude with two speeds: how *hard* it pulses is
 * part of the signal, and a warning pulsing as strongly as a healthy tunnel
 * would read as healthy.
 */
internal data class Breath(val peak: Float, val periodMs: Int)

internal fun breathFor(state: ConnectionState, active: Boolean): Breath? = when {
    // Paused screen (§5.5) or reduced motion: decoration stops, the glow stays.
    !active -> null
    state == ConnectionState.CONNECTED -> Breath(BREATH_MAX_SCALE, StarveilMotion.BREATH_PERIOD_MS)
    state == ConnectionState.WARNING ->
        Breath(WARNING_BREATH_MAX_SCALE, StarveilMotion.WARNING_BREATH_PERIOD_MS)
    else -> null
}

/**
 * Whether the system is animating at all.
 *
 * The ambient loops must not run when it is not. An `infiniteRepeatable` tween
 * is scaled by the animator duration scale, so with animations switched off its
 * period collapses to zero and the loop becomes a busy loop burning frames —
 * precisely what "reduce motion" asked us not to do. Design system §5.5:
 * reduced motion turns the breathing and the rotation into a static glow.
 *
 * Read once per composition: this is an accessibility toggle, not something
 * that changes while a screen is open.
 */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }
}
