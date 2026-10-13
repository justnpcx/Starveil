package com.justnpcx.starveil.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion spec — one source of truth for animation feel.
 *
 * Rules:
 * - State changes (connect/disconnect) use [StateTransition]: fast, decisive.
 * - Continuous ambience (breathing, pulsing) uses [AmbientLoop].
 * - Never animate layout size for state changes; animate alpha/scale/rotation.
 */
object StarveilMotion {
    /** State transitions: 220ms, expressive ease. */
    val StateTransition = tween<Float>(durationMillis = 220, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))

    /** Spring for press / toggle feedback. */
    fun <T> pressSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Ambient breathing loop period (ms). */
    const val BREATH_PERIOD_MS = 2600

    /** Connecting rotation period (ms). */
    const val SPIN_PERIOD_MS = 1100

    /** Scale while a control is pressed. */
    const val PRESSED_SCALE = 0.96f
}
