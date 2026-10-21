package com.justnpcx.starveil.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette, as data.
 *
 * Two instances, one set of names. Every screen reads a token and never asks
 * which theme is in force — that is the whole point of the indirection: a page
 * that branches on `isDark` is a page with two designs in it, and they drift.
 *
 * Warm neutrals throughout. The greys this replaces were slate, which carries a
 * blue cast; on a white page that reads as dirty rather than neutral, and it is
 * the single thing that made the old UI look unfinished next to the wallpapers
 * it sat on. Every neutral here has red and green above blue.
 */
@Immutable
data class StarveilPalette(
    /** Page background. Warm white, never pure white. */
    val bg: Color,
    /** A raised surface. Only for things that float over the page: sheets, dialogs. */
    val surface: Color,
    /** Primary text. Warm black, never pure black. */
    val ink: Color,
    /** Secondary text: labels, descriptions. */
    val ink2: Color,
    /** Tertiary: placeholders, timestamps, disabled values. */
    val ink3: Color,
    /** The one divider colour. 1dp, inset to the text column. */
    val hairline: Color,
    /** Champagne gold. Lines and dots only — never a fill. */
    val gold: Color,
    /** Gold at text weight: small labels that must still clear AA. */
    val goldDeep: Color,
    /** The only permitted wash, for a selected row. */
    val goldWash: Color,
    /** Healthy. Muted jade, no fluorescence. */
    val ok: Color,
    /** Attention. Amber, deeper than the gold so the two read as different things. */
    val warn: Color,
    /** Failure, blocking. Brick, low saturation. */
    val err: Color,
) {
    /** A divider, at the width the design calls a hairline. */
    val hairlineWidth = 1f
}

/** Light: warm white paper, warm black ink, champagne. */
val LightPalette = StarveilPalette(
    bg = Color(0xFFFAF9F6),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF1A1815),
    ink2 = Color(0xFF6E675C),
    ink3 = Color(0xFFA39A8B),
    hairline = Color(0xFFE5E0D5),
    // AA8A50 rather than the spec's B08D57: the spec's value measures 2.94:1
    // on this page, under the 3:1 a meaningful graphic needs, and the ring is a
    // graphic. Two steps darker is the smallest change that clears it (3.10:1)
    // while still reading as champagne rather than brass.
    gold = Color(0xFFAA8A50),
    goldDeep = Color(0xFF8C6D3F),
    goldWash = Color(0xFFF3EFE6),
    ok = Color(0xFF2F7D5B),
    // 9E6719 rather than B7791F: the spec's amber measures 3.46:1, below AA for
    // the availability percentage it is drawn as. This is the same hue at the
    // value where it clears 4.5:1 (4.52:1).
    warn = Color(0xFF9E6719),
    err = Color(0xFFA63D2F),
)

/**
 * Dark: "night gauze".
 *
 * The same layout and the same names, lit from the other side. The gold lifts
 * because a champagne that reads as metal on paper reads as mud on a dark
 * ground; the status colours lift for the same reason, by the same amount.
 */
val DarkPalette = StarveilPalette(
    bg = Color(0xFF121110),
    surface = Color(0xFF1C1A17),
    ink = Color(0xFFF2EDE4),
    ink2 = Color(0xFFA79C8C),
    ink3 = Color(0xFF6E675C),
    hairline = Color(0xFF2E2A24),
    gold = Color(0xFFC8A468),
    goldDeep = Color(0xFFB08D57),
    goldWash = Color(0xFF262118),
    ok = Color(0xFF4CA67F),
    warn = Color(0xFFD29A3A),
    // C76352 rather than C05B4A: on a dark page the spec's brick measures
    // 4.34:1, just under AA for the error text it is used as. Two steps lighter
    // keeps it brick rather than salmon and clears 4.5:1 (4.79:1).
    err = Color(0xFFC76352),
)