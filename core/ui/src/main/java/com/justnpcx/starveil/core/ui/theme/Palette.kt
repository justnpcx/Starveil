package com.justnpcx.starveil.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The palette. One instance, one set of names.
 *
 * Starveil has one face: obsidian. A pure black page, a near-black glass card
 * one step above it, and a single cyan-to-violet accent that is spent on the
 * ring, a selected marker and an on switch — nothing else.
 *
 * There is no second palette and no branch on "which theme". The indirection
 * that remains (a CompositionLocal, so Material components resolve the same
 * values) exists for the components, not for a choice the user can make.
 *
 * The greys this replaces were slate and warm-neutral by turns, and both read
 * as dirty against black: a grey with a blue cast goes muddy, a grey with a red
 * cast goes brown. These are cool and desaturated instead, and the only
 * saturated colours in the app are the accent and the three statuses.
 */
@Immutable
data class StarveilPalette(
    /** Page background. Pure black — OLED black, not a dark grey pretending. */
    val bg: Color,
    /** A glass card, a sheet. One step off black, never a grey block. */
    val surfaceGlass: Color,
    /** The 1dp edge that gives a glass card its shape. */
    val glassBorder: Color,
    /** Dividers inside a card, and the line above the navigation bar. */
    val hairline: Color,
    /** Primary text. */
    val ink: Color,
    /** Secondary text: labels, descriptions. */
    val ink2: Color,
    /** Tertiary: placeholders, timestamps, unselected icons. */
    val ink3: Color,
    /** The gradient's start: electric cyan. */
    val accentA: Color,
    /** The gradient's end: violet. */
    val accentB: Color,
    /** Online, healthy. */
    val ok: Color,
    /** Attention. */
    val warn: Color,
    /** Failure, blocking. */
    val err: Color,
    /** The ring at rest: the one grey it is allowed to be when nothing runs. */
    val ringIdle: Color,
) {
    /** A divider, at the width the design calls a hairline. */
    val hairlineWidth = 1f
}

/** The palette. There is one. */
val ObsidianPalette = StarveilPalette(
    bg = Color(0xFF000000),
    surfaceGlass = Color(0xFF0B0D10),
    glassBorder = Color(0xFF1E2430),
    hairline = Color(0xFF161B24),
    ink = Color(0xFFF5F7FA),
    ink2 = Color(0xFF8A94A6),
    ink3 = Color(0xFF5B6577),
    accentA = Color(0xFF22D3EE),
    accentB = Color(0xFF8B5CF6),
    ok = Color(0xFF2FBF9B),
    warn = Color(0xFFE8B33D),
    err = Color(0xFFE05B4A),
    // The spec's idle ring. Not a token for anything else: it is the ring at
    // rest, one step above the card's edge so the circle still has a shape.
    ringIdle = Color(0xFF232833),
)

/**
 * The accent, as a gradient.
 *
 * 135 degrees, cyan to violet, and only ever a stroke or a small fill. A
 * gradient that fills an area is the fastest way to make a black screen look
 * cheap; the health curve's fade under the line is the one exception, and it
 * fades to nothing rather than to a colour.
 */
object StarveilAccent {

    /** The two stops, in order. */
    val colors: List<Color> = listOf(ObsidianPalette.accentA, ObsidianPalette.accentB)

    /** The gradient across [size], running top-left to bottom-right. */
    fun brush(size: Size): Brush = Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )

    /** The gradient at a fraction of its opacity — the on switch's track. */
    fun brush(size: Size, alpha: Float): Brush = Brush.linearGradient(
        colors = colors.map { it.copy(alpha = alpha) },
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
}
