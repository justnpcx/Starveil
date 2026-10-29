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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Size
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
import com.justnpcx.starveil.core.ui.theme.StarveilBrand
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
 * ConnectionCore — the ring, and the one control on the home screen.
 *
 * 204dp, always. The ring is the page's anchor: the rates, the connection card
 * and the health card are measured from it, so a state change may move exactly
 * one thing — the colour.
 *
 * Inside the ring, top to bottom: the star, the state word, and one line that
 * says what a tap will do. Connected draws the brand gradient; disconnected
 * draws the same shape hollow in [StarveilColors.ringOff].
 */
@Composable
fun ConnectionCore(
    state: ConnectionState,
    onClick: () -> Unit,
    /** Required: this is the screen's primary control. */
    contentDescription: String,
    /** The state, drawn inside the ring. */
    label: String,
    modifier: Modifier = Modifier,
    /** One line under the state: what the next tap does. */
    hint: String? = null,
    /** The ring's diameter. Fixed at 204dp in every state. */
    diameter: Dp = CORE_SIZE,
    /** How much of the box's bottom the drawing may overflow. */
    bottomTrim: Dp = 0.dp,
    /** A connect or disconnect is in flight. */
    busy: Boolean = false,
) {
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

    val accent = StarveilColors.accent
    val warnColor = StarveilColors.warn
    val errColor = StarveilColors.err
    val idleRing = StarveilColors.ringOff

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
        Canvas(modifier = Modifier.align(Alignment.TopCenter).size(diameter)) {
            val stroke = Stroke(width = RING_STROKE.toPx(), cap = StrokeCap.Round)
            val outer = size.minDimension / 2
            val ringRadius = outer - RING_STROKE.toPx() / 2
            val statusColor = when (state) {
                ConnectionState.WARNING -> warnColor
                ConnectionState.BLOCKED -> errColor
                else -> null
            }

            when {
                state == ConnectionState.DISCONNECTED ->
                    drawCircle(color = idleRing, radius = ringRadius, style = stroke)

                statusColor != null ->
                    drawCircle(color = statusColor, radius = ringRadius, style = stroke)

                else -> {
                    // The brand gradient, laid across the ring's bounding box.
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = StarveilBrand.colors,
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        radius = ringRadius,
                        style = stroke,
                    )

                    if (state == ConnectionState.CONNECTING) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(Color.Transparent, Color.White, Color.Transparent),
                                center = center,
                            ),
                            startAngle = rotation - 90f,
                            sweepAngle = 100f,
                            useCenter = false,
                            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                            size = Size(ringRadius * 2, ringRadius * 2),
                            style = stroke,
                        )
                    }

                    if (sweep.value > 0f && sweep.value < 1f) {
                        drawArc(
                            color = Color.White.copy(alpha = 1f - sweep.value),
                            startAngle = -90f + 360f * sweep.value,
                            sweepAngle = 120f,
                            useCenter = false,
                            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                            size = Size(ringRadius * 2, ringRadius * 2),
                            style = stroke,
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = bottomTrim / 2),
        ) {
            StarveilStar(
                filled = state == ConnectionState.CONNECTED || state == ConnectionState.WARNING,
                size = 30.dp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = StarveilType.ringWord,
                color = if (state == ConnectionState.BLOCKED) StarveilColors.err else StarveilColors.ink,
                textAlign = TextAlign.Center,
            )
            if (hint != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hint,
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The ring's diameter. Fixed: the page is measured from this box. */
private val CORE_SIZE = 204.dp

/** 5dp, as the task book draws it. */
private val RING_STROKE = 5.dp

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
 * composable. The size never varies with state.
 */
object ConnectionCoreGeometry {

    /** The stroke, in dp. */
    const val strokeDp = 5f

    /** The diameter, in dp. */
    const val diameterDp = 204f

    /** The diameter for a given available height — always the same. */
    @Suppress("UNUSED_PARAMETER")
    fun diameterFor(availableHeightDp: Float): Float = diameterDp

    /** The size in a given state — the same number every time. */
    @Suppress("UNUSED_PARAMETER")
    fun sizeFor(state: ConnectionState): Float = diameterDp
}
