package com.justnpcx.starveil.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilMotion
import kotlinx.coroutines.delay

/** §5.2: how far an appearing row travels. */
private val APPEAR_TRAVEL = 20.dp

/** §5.2: the gap between one row's entrance and the next. */
private const val APPEAR_STAGGER_MS = 30L

/**
 * How many steps of stagger are honoured.
 *
 * The spec asks for 30ms per item, which assumes the whole list appears at
 * once. It does not: a LazyColumn composes rows as they are needed, so with an
 * uncapped delay, scrolling down to node 50 would sit on a blank row for
 * 1.5 seconds before its entrance even started. Capping keeps the top-down
 * settle the spec is after without punishing deep rows.
 */
private const val APPEAR_MAX_STAGGER_STEPS = 6

/**
 * Remembers which rows have already made their entrance.
 *
 * Hoist this above the list. A LazyColumn disposes rows that scroll out of
 * view, so state kept inside a row would replay its entrance every time it
 * came back.
 */
@Composable
fun rememberAppearance(): MutableSet<Any> = remember { mutableSetOf() }

/**
 * §5.2: a list row fades and slides into place the first time it is shown, one
 * after another rather than all at once.
 *
 * [key] identifies the row; [seen] is the hoisted set from [rememberAppearance].
 * [index] is its position, which sets the stagger — see
 * [APPEAR_MAX_STAGGER_STEPS] for why it is capped.
 */
@Composable
fun AppearOnFirstShow(
    key: Any,
    index: Int,
    seen: MutableSet<Any>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Recorded during composition on purpose: the first frame has to know
    // whether this row is new, or it would flash in before the entrance starts.
    val isNew = remember(key) { seen.add(key) }
    var shown by remember(key) { mutableStateOf(!isNew) }

    LaunchedEffect(key) {
        if (isNew) {
            delay(index.coerceAtMost(APPEAR_MAX_STAGGER_STEPS) * APPEAR_STAGGER_MS)
            shown = true
        }
    }

    val travelPx = with(LocalDensity.current) { APPEAR_TRAVEL.roundToPx() }

    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(animationSpec = StarveilMotion.stateTransition()) +
            slideInVertically(
                animationSpec = StarveilMotion.stateTransition(),
                initialOffsetY = { travelPx },
            ),
        modifier = modifier,
    ) {
        content()
    }
}
