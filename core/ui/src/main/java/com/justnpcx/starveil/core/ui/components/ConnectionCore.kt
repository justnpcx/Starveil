package com.justnpcx.starveil.core.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.justnpcx.starveil.core.ui.theme.StarveilAccent
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilMotion
import com.justnpcx.starveil.core.ui.theme.StarveilType

/** Connection states. The core's visuals are a pure function of this state. */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,

    /**
     * Up, but not trustworthy — the tunnel is carrying traffic and something
     * about it is wrong (a leak the self-check found), or it is connected and
     * carrying nothing. Distinct from [BLOCKED], which is traffic not leaving
     * at all.
     */
    WARNING,
    BLOCKED,
}

/**
 * ConnectionCore — the ring, and the one control on the dashboard.
 *
 * The ring is a 4dp stroke: the accent gradient when the tunnel is carrying, a
 * cyan sweep while it connects, the idle grey when it is off, amber when it is
 * up but silent, red when it is blocked.
 *
 * **The size never changes.** Not on a state change, not while connecting, not
 * for a warning. The ring is the anchor the whole page is measured from — the
 * rates below it, the node card, the health card — and a ring that grows by 6%
 * moves every one of them. A state change moves exactly one thing: the colour.
 */
@Composable
fun ConnectionCore(
    state: ConnectionState,
    onClick: () -> Unit,
    /** Required: this is the screen's primary control (design system §6.1). */
    contentDescription: String,
    /**
     * The state, drawn inside the ring. The ring is the only control on the
     * screen, so its centre is the one place the state cannot be missed.
     */
    label: String,
    modifier: Modifier = Modifier,
    /** The ring's diameter. Fixed at 240dp in every state. */
    diameter: Dp = CORE_SIZE,
    /**
     * How much of the box's bottom the drawing may overflow.
     *
     * The ring sits [RING_INSET] inside its box, so without this the layout
     * carries ~22 dp of empty space under the circle and the next block starts
     * too far away.
     */
    bottomTrim: Dp = 0.dp,
    /**
     * A connect or disconnect is in flight. Drawn as the same sweep as
     * CONNECTING and the tap is ignored.
     */
    busy: Boolean = false,
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
    val spinning = resumed && animationsEnabled && (state == ConnectionState.CONNECTING || busy)

    val spin = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) {
            spin.snapTo(0f)
            return@LaunchedEffect
        }
        spin.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = StarveilMotion.SPIN_PERIOD_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )
    }

    // The sweep runs once, on arrival at CONNECTED. Keyed on the state so a
    // recomposition cannot restart it; a ring that sweeps every time the rates
    // tick would be the busiest thing on the screen.
    val sweep = remember { Animatable(0f) }
    var lastState by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        val from = lastState
        lastState = state
        if (state != ConnectionState.CONNECTED || from == ConnectionState.CONNECTED) return@LaunchedEffect
        if (!animationsEnabled) return@LaunchedEffect
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(durationMillis = StarveilMotion.CONNECT_SWEEP_MS, easing = FastOutSlowInEasing))
    }

    // Haptics ride the *transition*, not the press: the state change is what
    // the user is waiting on.
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
            ConnectionState.WARNING -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            ConnectionState.CONNECTING -> Unit
        }
    }

    val rotation = if (state == ConnectionState.CONNECTING) spin.value else 0f

    // Resolved before the Canvas: the token getters are @Composable reads, and
    // a DrawScope lambda is not a composable context.
    val accentA = StarveilColors.accentA
    val warnColor = StarveilColors.warn
    val errColor = StarveilColors.err
    val idleRing = StarveilColors.ringIdle
    val accentStops = StarveilAccent.colors

    Box(
        modifier = modifier
            .width(diameter)
            .height(diameter - bottomTrim)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(diameter),
        ) {
            val stroke = Stroke(width = RING_STROKE.toPx(), cap = StrokeCap.Round)
            val outer = size.minDimension / 2
            val ringRadius = outer - RING_INSET.toPx()

            val lit = state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
            val statusColor = when (state) {
                ConnectionState.WARNING -> warnColor
                ConnectionState.BLOCKED -> errColor
                else -> null
            }

            // The glow: one radial gradient, peaking on the ring and fading both
            // ways over ~24dp. A continuous falloff, one draw call, and no blur
            // pass — a RenderEffect would cost GPU work every frame and only
            // exists from API 31 (minSdk is 29).
            if (lit || statusColor != null) {
                val glowColor = statusColor ?: accentA
                val spread = GLOW_SPREAD.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            ((ringRadius - spread) / outer).coerceIn(0f, 1f) to Color.Transparent,
                            (ringRadius / outer).coerceIn(0f, 1f) to glowColor.copy(alpha = GLOW_PEAK_ALPHA),
                            ((ringRadius + spread) / outer).coerceIn(0f, 1f) to Color.Transparent,
                        ),
                        center = center,
                        radius = outer,
                    ),
                    radius = outer,
                )
            }

            when {
                // Idle: a line, not a dimmed accent. A gradient ring on an idle
                // app would make the accent mean "this exists" rather than
                // "this is working".
                state == ConnectionState.DISCONNECTED ->
                    drawCircle(color = idleRing, radius = ringRadius, style = stroke)

                statusColor != null ->
                    drawCircle(color = statusColor, radius = ringRadius, style = stroke)

                else -> {
                    // 135° across the ring's box: cyan at the top-left, violet
                    // at the bottom-right.
                    val ringBrush = Brush.linearGradient(
                        colors = accentStops,
                        start = Offset(center.x - ringRadius, center.y - ringRadius),
                        end = Offset(center.x + ringRadius, center.y + ringRadius),
                    )
                    drawCircle(brush = ringBrush, radius = ringRadius, style = stroke)

                    if (state == ConnectionState.CONNECTING) {
                        // The charge arc: a bright run of the same gradient,
                        // rotating. It says "being drawn", which the colour
                        // alone cannot.
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    accentA,
                                    Color.Transparent,
                                ),
                                center = center,
                            ),
                            startAngle = rotation - 90f,
                            sweepAngle = 100f,
                            useCenter = false,
                            style = stroke,
                        )
                    }

                    if (sweep.value > 0f && sweep.value < 1f) {
                        // The one-shot success sweep, fading as it completes.
                        drawArc(
                            color = accentA.copy(alpha = 1f - sweep.value),
                            startAngle = -90f + 360f * sweep.value,
                            sweepAngle = 120f,
                            useCenter = false,
                            style = stroke,
                        )
                    }
                }
            }
        }

        Text(
            text = label,
            style = StarveilType.ringWord,
            // Blocked borrows the ring's colour: the state is a warning, and
            // colour is the part that reads before the word does. Every other
            // state uses ink, so the word is legible without a plate.
            color = if (state == ConnectionState.BLOCKED) StarveilColors.err else StarveilColors.ink,
            textAlign = TextAlign.Center,
            // The box is trimmed at the bottom, so its centre is above the
            // ring's; move the word back onto the ring's centre line.
            modifier = Modifier.offset(y = bottomTrim / 2),
        )
    }
}

/** The ring's diameter. Fixed: the page is measured from this box. */
private val CORE_SIZE = 240.dp

/** 4dp, and a stroke that does not scale with the diameter. */
private val RING_STROKE = 4.dp

/** How far the ring sits inside its box, leaving room for the glow. */
private val RING_INSET = 22.dp

/** How far the glow reaches either side of the ring (~24dp total). */
private val GLOW_SPREAD = 12.dp

/**
 * The glow's peak. 30% of the accent: present enough to read as light, faint
 * enough that it never becomes a filled disc.
 */
private const val GLOW_PEAK_ALPHA = 0.30f

/**
 * Whether the system is animating at all.
 *
 * An `infiniteRepeatable` tween is scaled by the animator duration scale, so
 * with animations switched off its period collapses to zero and the loop
 * becomes a busy loop burning frames.
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

/**
 * The ring's geometry, as a function rather than a constant buried in a
 * composable.
 *
 * Two facts live here and both are contracts the rest of the dashboard depends
 * on: the size never varies with state, and there is exactly one size.
 */
object ConnectionCoreGeometry {

    /** The stroke, in dp. */
    const val strokeDp = 4f

    /** The diameter, in dp. */
    const val diameterDp = 240f

    /** The diameter for a given available height — always the same. */
    @Suppress("UNUSED_PARAMETER")
    fun diameterFor(availableHeightDp: Float): Float = diameterDp

    /** The size in a given state — the same number every time. */
    @Suppress("UNUSED_PARAMETER")
    fun sizeFor(state: ConnectionState): Float = diameterDp
}
