package com.justnpcx.starveil.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion spec — one source of truth for animation feel.
 *
 * Rules:
 * - State changes (connect/disconnect) use [stateTransition]: fast, decisive.
 * - Continuous ambience (breathing, pulsing) uses [AmbientLoop].
 * - Never animate layout size for state changes; animate alpha/scale/rotation.
 */
object StarveilMotion {
    /** The state-transition curve: 220ms, expressive ease. */
    private val STATE_EASING = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /**
     * A state transition, for whatever is being animated. Generic because one
     * state change moves several things at once — a colour, a radius, a scale
     * — and they have to move together on the same curve (design system §5.3).
     */
    fun <T> stateTransition(): TweenSpec<T> = tween(durationMillis = 220, easing = STATE_EASING)

    /** Spring for press / toggle feedback. */
    fun <T> pressSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Ambient breathing loop period (ms). */
    const val BREATH_PERIOD_MS = 2600

    /**
     * Warning breathing period (ms) — slower than [BREATH_PERIOD_MS] on
     * purpose. A warning that pulses at the healthy rate reads as "all good"
     * with the wrong colour; the slower, shallower pulse is what makes it
     * read as attention rather than life.
     */
    const val WARNING_BREATH_PERIOD_MS = 3600

    /** Connecting rotation period (ms). */
    const val SPIN_PERIOD_MS = 1100

    /** Scale while a control is pressed. */
    const val PRESSED_SCALE = 0.96f
}
