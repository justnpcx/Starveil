package com.justnpcx.starveil.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A semantic pair for one hub row: the icon's bed and the glyph on it.
 *
 * Not brand gradient, and not a status colour: these four are a wayfinding
 * code, one hue per destination, and they exist in both modes.
 */
@Immutable
data class HubTone(val bg: Color, val ink: Color)

/**
 * The palette: two modes, one set of role names.
 *
 * The task book's token table is the only source of these values. A screen asks
 * for a role (`ink`, `card`, `selBg`) and never for a hex; the mode is decided
 * once, above the whole app.
 *
 * The old glass-era names are kept as aliases at the bottom of this file. They
 * are not a second palette: each one resolves to one of the roles above, so the
 * screens that still spell `surfaceGlass` cannot drift away from `card`.
 */
@Immutable
data class StarveilPalette(
    /** True for the dark table. */
    val dark: Boolean,
    /** The page. */
    val bg: Color,
    /** A card, a dialog, a menu, a sheet. Opaque by design. */
    val card: Color,
    /** Primary text. */
    val ink: Color,
    /** Secondary text, icons, labels. */
    val ink2: Color,
    /** A rule between rows. */
    val line: Color,
    /** The outline of an unselected chip. */
    val chipB: Color,
    /** The functional accent, safe as text. */
    val accent: Color,
    /** The fill behind a selected pill/segment. */
    val selBg: Color,
    /** The text on [selBg]. */
    val selInk: Color,
    /** The trough of a segmented control. */
    val segBg: Color,
    /** Failure, as a dot, a line or text. */
    val err: Color,
    /** Healthy. */
    val ok: Color,
    /** Attention. */
    val warn: Color,
    /** The ring when nothing is connected. */
    val ringOff: Color,
    /** A brand mark's bed: the pale accent a link icon sits on. */
    val accentSoft: Color,
    /** The plus disc's face. The ring around it is the brand gradient. */
    val floatDisc: Color,
    /** A switch, off: the trough. */
    val swOff: Color,
    /** A switch, off: the outline and the thumb. */
    val swOut: Color,
    /** A switch, on: the thumb. */
    val swOnTh: Color,
    /** A switch, on: the check inside the thumb. */
    val swChk: Color,
    /** The hub's four entry tones, one per destination. */
    val hubScenes: HubTone,
    val hubLogs: HubTone,
    val hubDiagnostics: HubTone,
    val hubSettings: HubTone,
    /** The hub's about row: a bed for the C2 mark, which never re-tints. */
    val hubAboutBed: Color,
    /** The gauze woven across the connection card. */
    val veil: Color,
    /** Page bloom: pink, blue, purple. Static, never animated. */
    val washPink: Color,
    val washBlue: Color,
    val washPurple: Color,
    /** The tint every shadow is drawn in. */
    val shadowTint: Color,
) {
    /** A hairline is one pixel at every density. */
    val hairlineWidth = 1f

    /** The health curve's fill. */
    val curveFillAlpha = 0.14f

    /** The three blooms, in paint order. */
    val washes: List<Color> get() = listOf(washPink, washBlue, washPurple)

    // ---- Legacy role names, each mapped to exactly one token above ----------

    /** @deprecated The card is opaque now; use [card]. */
    val surfaceGlass: Color get() = card

    /** @deprecated Inputs and wells use the segment trough; use [segBg]. */
    val surfaceThin: Color get() = segBg

    /** @deprecated Floating surfaces are cards; use [card]. */
    val surfaceSolid: Color get() = card

    /** @deprecated A card's edge is the line token; use [line]. */
    val glassBorder: Color get() = line

    /** @deprecated Use [line]. */
    val hairline: Color get() = line

    /** @deprecated Use [line]. */
    val divider: Color get() = line

    /** @deprecated Tertiary text is the secondary grade; use [ink2]. */
    val ink3: Color get() = ink2

    /** @deprecated Accent text is the accent; use [accent]. */
    val accentText: Color get() = accent

    /** @deprecated Failure text is the failure colour; use [err]. */
    val errText: Color get() = err

    /** @deprecated The idle ring is [ringOff]. */
    val ringIdle: Color get() = ringOff

    /** A selected row's wash, now the selected fill. */
    val accentWash: Color get() = selBg

    /** The page is flat; kept so old call sites still compile. */
    val bgTop: Color get() = bg
    val bgMid: Color get() = bg
    val bgBottom: Color get() = bg
}

/** The light table, exactly as the task book lists it. */
val LightPalette = StarveilPalette(
    dark = false,
    bg = Color(0xFFEDF0F4),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF1F1F1F),
    ink2 = Color(0xFF444746),
    line = Color(0xFFDCE1E8),
    chipB = Color(0xFFC4C7C5),
    accent = Color(0xFF0B57D0),
    selBg = Color(0xFFD3E3FD),
    selInk = Color(0xFF0B57D0),
    segBg = Color(0xFFE4EAF1),
    err = Color(0xFFB3261E),
    ok = Color(0xFF137333),
    warn = Color(0xFFAC4D00),
    ringOff = Color(0xFFC4C7C5),
    accentSoft = Color(0xFFE8F0FE),
    floatDisc = Color(0xFFFFFFFF),
    swOff = Color(0xFFE9EAEC),
    swOut = Color(0xFF747775),
    swOnTh = Color(0xFFFFFFFF),
    swChk = Color(0xFF0B57D0),
    hubScenes = HubTone(bg = Color(0xFFEFEBFB), ink = Color(0xFF6C4BD8)),
    hubLogs = HubTone(bg = Color(0xFFE8F0FE), ink = Color(0xFF0B57D0)),
    hubDiagnostics = HubTone(bg = Color(0xFFE4F3EC), ink = Color(0xFF137333)),
    hubSettings = HubTone(bg = Color(0xFFFDEEE2), ink = Color(0xFFD96C00)),
    hubAboutBed = Color(0xFFF1EFFD),
    veil = Color(0x177C6CF0),
    washPink = Color(0x1AE85D9E),
    washBlue = Color(0x1A4285F4),
    washPurple = Color(0x147C6CF0),
    shadowTint = Color(0xFF1F2687),
)

/** The dark table. Not pure black: the page is #131314, the card #1E1F20. */
val DarkPalette = StarveilPalette(
    dark = true,
    bg = Color(0xFF131314),
    card = Color(0xFF1E1F20),
    ink = Color(0xFFE3E3E3),
    ink2 = Color(0xFFC4C7C5),
    line = Color(0xFF3F4446),
    chipB = Color(0xFF5F6368),
    accent = Color(0xFFA8C7FA),
    selBg = Color(0xFF004A77),
    selInk = Color(0xFFC2E7FF),
    segBg = Color(0xFF28292A),
    err = Color(0xFFF2B8B5),
    ok = Color(0xFF81C995),
    // Measured 10.92:1 on bg / 9.71:1 on card, so the candidate stands.
    warn = Color(0xFFFFB59E),
    ringOff = Color(0xFF5F6368),
    accentSoft = Color(0xFF26303F),
    floatDisc = Color(0xFF232426),
    swOff = Color(0xFF36373B),
    swOut = Color(0xFF8E918F),
    swOnTh = Color(0xFF0B57D0),
    swChk = Color(0xFFD3E3FD),
    hubScenes = HubTone(bg = Color(0xFF2C2440), ink = Color(0xFFB79CFF)),
    hubLogs = HubTone(bg = Color(0xFF22344D), ink = Color(0xFF8AB4F8)),
    hubDiagnostics = HubTone(bg = Color(0xFF1E3328), ink = Color(0xFF81C995)),
    hubSettings = HubTone(bg = Color(0xFF3A2A1A), ink = Color(0xFFF0A35E)),
    hubAboutBed = Color(0xFF2A2440),
    veil = Color(0x12A8C7FA),
    washPink = Color(0x1FE85D9E),
    washBlue = Color(0x1F4285F4),
    washPurple = Color(0x247C6CF0),
    shadowTint = Color(0xFF000000),
)

/** The default palette. The theme always provides one explicitly. */
val GlassPalette = LightPalette
