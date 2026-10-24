package com.justnpcx.starveil.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette. One instance, one set of names.
 *
 * Starveil has one face: frosted glass. A light, slightly cool page, a
 * translucent white material one layer above it, and a single blue spent on
 * selected marks, switches and status. Nothing glows, nothing is pure black,
 * and no surface is fully opaque — every card shows a little of what is under
 * it, which is what keeps a full screen of text from reading as a glare.
 *
 * There is no second palette and no branch on "which theme". The indirection
 * that remains (a CompositionLocal, so Material components resolve the same
 * values) exists for the components, not for a choice the user can make.
 *
 * Every value below was read from the 03 mockup in the design demo — its own
 * background gradient, the card's 68% white and 18px backdrop blur, the 75%
 * white edge, and the two blues the mockup actually uses (#0071E3 for marks,
 * #0066CC for accent-coloured text). The exceptions are called out one by one:
 * `warn`, and the text grades that had to be darkened to clear 4.5:1 here.
 */
@Immutable
data class StarveilPalette(
    /** The page's flat base. The gradient is painted on top of it. */
    val bg: Color,
    /** The ambient gradient, 160 degrees, cool to warm. */
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    /** A glass card: 68% white, one step above the page. */
    val surfaceGlass: Color,
    /** Thinner material: inputs, flag chips, small wells. */
    val surfaceThin: Color,
    /** The navigation bar's material. */
    val surfaceChrome: Color,
    /**
     * The same material with the transparency resolved. A dialog or a menu sits
     * over a dim scrim, and 68% white over a scrim is grey — so anything that
     * floats above a dimmed page uses this instead of [surfaceGlass].
     */
    val surfaceSolid: Color,
    /** The 1dp highlight edge that gives a glass card its shape. */
    val glassBorder: Color,
    /** Dividers inside a card, and the line above the navigation bar. */
    val hairline: Color,
    /** Primary text. */
    val ink: Color,
    /** Secondary text: labels, descriptions. */
    val ink2: Color,
    /** Tertiary: placeholders, timestamps, unselected icons. */
    val ink3: Color,
    /** The one accent: selected marks, switches, status dots. */
    val accent: Color,
    /** The accent as text. A shade darker, because 4.01:1 is not 4.5:1. */
    val accentText: Color,
    /** Online, healthy: text, dots and the ring's healthy state. */
    val ok: Color,
    /** Attention. Not in the demo; fixed at 4.68:1 on the page. */
    val warn: Color,
    /** Failure, as a dot or a line. */
    val err: Color,
    /** Failure as text, darkened to clear 4.5:1. */
    val errText: Color,
    /**
     * The tint of every shadow the app draws. One value, because a shadow is
     * part of the material and not a per-component decision.
     */
    val shadowTint: Color,
) {
    /** A divider, at the width the design calls a hairline. */
    val hairlineWidth = 1f

    /** A selected row's wash: the accent at 10%, the only tinted fill allowed. */
    val accentWash: Color get() = accent.copy(alpha = 0.10f)

    /** The health curve's fill: the demo's 14%, under a 1.5dp line. */
    val curveFillAlpha = 0.14f

    /** The ambient washes that make the material visible: blue, green, amber. */
    val washes: List<Color> get() = listOf(WASH_BLUE, WASH_GREEN, WASH_AMBER)
}

private val WASH_BLUE = Color(0xFF5AA0FF).copy(alpha = 0.28f)
private val WASH_GREEN = Color(0xFF50D2AA).copy(alpha = 0.22f)
private val WASH_AMBER = Color(0xFFFFAA5A).copy(alpha = 0.20f)

/** The palette. There is one. */
val GlassPalette = StarveilPalette(
    bg = Color(0xFFE9EDF2),
    bgTop = Color(0xFFE8EEF5),
    bgMid = Color(0xFFEEF3EE),
    bgBottom = Color(0xFFF3ECE4),
    surfaceGlass = Color(0xFFFFFFFF).copy(alpha = 0.68f),
    surfaceThin = Color(0xFFFFFFFF).copy(alpha = 0.55f),
    surfaceChrome = Color(0xFFFFFFFF).copy(alpha = 0.60f),
    surfaceSolid = Color(0xFFF6F8FA),
    glassBorder = Color(0xFFFFFFFF).copy(alpha = 0.75f),
    hairline = Color(0xFFFFFFFF).copy(alpha = 0.75f),
    ink = Color(0xFF1D1D1F),
    // The demo's 60% and 42% are Apple's own secondary and tertiary grades;
    // both land under AA on this page, so each is lifted one notch — 62% and
    // 48% — which keeps the step between them and puts ink2 at 4.59:1.
    ink2 = Color(0xFF1D1D1F).copy(alpha = 0.62f),
    ink3 = Color(0xFF1D1D1F).copy(alpha = 0.48f),
    accent = Color(0xFF0071E3),
    accentText = Color(0xFF0066CC),
    // The demo's status dot is a brighter green (#30A46C), but on this page
    // that is 2.68:1 — under the 3:1 a graphic that carries meaning needs. One
    // green, at 4.58:1, for text and dots alike.
    ok = Color(0xFF1D7A3A),
    warn = Color(0xFFAC4D00),
    err = Color(0xFFE5484D),
    errText = Color(0xFFBE3238),
    shadowTint = Color(0xFF1F2687),
)
