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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * ConnectionCore — the ring, and the one control on the dashboard.
 *
 * The ring is a line, not a filled shape: 3dp, gold when the tunnel is
 * carrying, hairline when it is idle, amber when it is up but silent, brick
 * when it is blocked. Nothing moves except the arc that turns while a connect
 * is in flight.
 *
 * **The size never changes.** Not on a state change, not while connecting, not
 * for a warning. The ring is the anchor the whole page is measured from — the
 * rates below it, the node row, the health line — and a ring that grows by 6%
 * moves every one of them. Earlier versions pulsed to look alive; the pulse was
 * the loudest thing on the screen, and it competed with the numbers.
 *
 * So a state change moves exactly one thing: the colour. That is deliberate and
 * it is enough — colour is what the eye catches first, and the word in the
 * middle says the rest.
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
    /** The ring's diameter. The dashboard shrinks it on short screens. */
    diameter: Dp = CORE_SIZE,
    /**
     * How much of the box's bottom the drawing may overflow.
     *
     * The ring sits [RING_INSET] inside its box, so without this the layout
     * carries ~22 dp of empty space under the circle and the next block starts
     * too far away. Trimming the box lets the following content sit close to
     * the visible ring while the drawing stays the same size.
     */
    bottomTrim: Dp = 0.dp,
    /**
     * A connect or disconnect is in flight. Drawn as the same charge arc as
     * CONNECTING and the tap is ignored: a swap can take seconds, and a
     * control that looks idle while it works reads as broken.
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
    // No breathing. The old core pulsed to show it was alive; this design shows
    // the same thing with a gold ring and a status word, and a 220–260dp circle
    // that changes size is the one movement the eye cannot ignore — it competes
    // with the numbers underneath, which are the thing being read.
    //
    // What is left is the rotation while connecting, which is information: the
    // ring is being drawn, not sitting there.
    val spinning = resumed && animationsEnabled && (state == ConnectionState.CONNECTING || busy)

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

    // Gold is the connected state, and only the connected state. An idle ring
    // is the hairline colour — a line, not a dimmed version of the accent —
    // because a gold ring on an idle app would make the accent mean "this
    // exists" rather than "this is working". Warning and blocked keep their own
    // colours: a status that shares the accent is a status nobody reads.
    val ringColor by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED -> StarveilColors.gold
            ConnectionState.CONNECTING -> StarveilColors.gold
            ConnectionState.DISCONNECTED -> StarveilColors.hairline
            ConnectionState.WARNING -> StarveilColors.warn
            ConnectionState.BLOCKED -> StarveilColors.err
        },
        animationSpec = StarveilMotion.stateTransition(),
        label = "ring-color",
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

    val rotation = if (state == ConnectionState.CONNECTING) spin.value else 0f

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

            // No centre dot: the state word sits there now. The glow around
            // the ring is what makes the core read as lit.
        }

        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
            // Blocked borrows the ring's colour: the state is a warning, and
            // colour is the part that reads before the word does. Every other
            // state uses onBackground — near-white in the dark theme, near-
            // black in the light one — so the word is legible without a plate.
            color = if (state == ConnectionState.BLOCKED) {
                ringColor
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            textAlign = TextAlign.Center,
            // The box is trimmed at the bottom, so its centre is above the
            // ring's; move the word back onto the ring's centre line.
            modifier = Modifier.offset(y = bottomTrim / 2),
        )
    }
}

private val CORE_SIZE = 220.dp
private val RING_STROKE = 3.dp
private val RING_INSET = 22.dp

/** How far the ring's glow reaches either side of it. */
private val GLOW_SPREAD = 14.dp

/**
 * The glow's peak. Barely there on purpose: at 0.5 the ring read as lit from
 * within, which is the old HUD. This design wants a halo you feel rather than
 * one you can point at.
 */
private const val GLOW_PEAK_ALPHA = 0.16f
private const val ARC_BRIGHTEN = 0.55f

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

/**
 * The ring's geometry, as a function rather than a constant buried in a
 * composable.
 *
 * Two facts live here and both are contracts the rest of the dashboard depends
 * on: the size never varies with state, and there are exactly two sizes, picked
 * by how much height the screen has. A third fact — that the ring is drawn, not
 * filled — is the stroke width.
 */
internal object ConnectionCoreGeometry {

    /** The stroke, in dp. */
    const val strokeDp = 3f

    /** The two diameters. */
    private const val LARGE_DP = 260f
    private const val SMALL_DP = 220f

    /**
     * Below this height the large ring leaves no room for the blocks under it,
     * so the small one is used. 720dp is the line the dashboard has always
     * drawn.
     */
    private const val SHORT_SCREEN_DP = 720f

    /** The diameter for a given available height. */
    fun diameterFor(availableHeightDp: Float): Float =
        if (availableHeightDp < SHORT_SCREEN_DP) SMALL_DP else LARGE_DP

    /**
     * The size in a given state — the same number every time.
     *
     * Takes the state so the signature says "this is a question you might ask
     * per state", and returns one value so the answer is on the record.
     */
    @Suppress("UNUSED_PARAMETER")
    fun sizeFor(state: ConnectionState): Float = LARGE_DP
}
