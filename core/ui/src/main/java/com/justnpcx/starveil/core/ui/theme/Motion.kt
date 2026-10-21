package com.justnpcx.starveil.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * Motion spec — one source of truth for animation feel.
 *
 * Rules:
 * - A state change is 240–300ms, ease-out. Nothing bounces: an overshoot on a
 *   switch or a ring reads as a toy, and this design is not playful.
 * - The ring's sweep on a successful connect runs once. It is the only
 *   decorative motion in the app.
 * - Never animate layout size for a state change; animate colour, alpha and
 *   rotation.
 */
object StarveilMotion {

    /** 260ms, ease-out: decisive without a snap. */
    private val STATE_EASING = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** A state transition, for whatever is being animated. */
    fun <T> stateTransition(): TweenSpec<T> = tween(durationMillis = 260, easing = STATE_EASING)

    /** Press feedback: fast, and no spring. */
    fun <T> press(): TweenSpec<T> = tween(durationMillis = 120, easing = STATE_EASING)

    /** Connecting rotation period (ms). */
    const val SPIN_PERIOD_MS = 1400

    /** How long the one-shot sweep after a successful connect takes. */
    const val CONNECT_SWEEP_MS = 720

    /** Scale while a control is pressed. */
    const val PRESSED_SCALE = 0.98f
}
