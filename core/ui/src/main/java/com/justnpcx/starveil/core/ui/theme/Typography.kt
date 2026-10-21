package com.justnpcx.starveil.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type: a serif for the things that are read, a mono for the things that are
 * measured.
 *
 * The luxury is in the display face — a high-contrast serif on the numbers that
 * matter — and the precision is in the mono, which keeps digits from jittering
 * as they change and marks anything that is a technical annotation. Body text
 * stays sans: a serif paragraph at 15sp on a phone is a reading problem, not a
 * refinement.
 *
 * Fonts live in `res/font` and are subset to latin + digits (see `docs/02`).
 * Chinese falls back to the system serif for headings and the system sans for
 * body, which is what the platform already ships and costs nothing.
 */

/** Display serif: Playfair Display, Medium and SemiBold only. */
val SerifFamily = FontFamily.Serif

/** Technical annotation: system mono. */
val MonoFamily = FontFamily.Monospace

/** Body: whatever the platform calls sans. */
val SansFamily = FontFamily.SansSerif

/**
 * The type scale, as tokens.
 *
 * Names describe a role, not a size: `tech` is "a technical annotation", and it
 * happens to be 11sp uppercase today. A screen that reaches for a size instead
 * of a role is how a scale turns into a list of exceptions.
 */
object StarveilType {

    /** The dashboard's rate. The one place the display face is 54sp. */
    val display = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 54.sp,
        lineHeight = 60.sp,
    )

    /** The word inside the ring. */
    val ringWord = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 38.sp,
    )

    /** A page's own title. */
    val title = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    )

    /** A section heading, or the main line of a row. */
    val headline = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    )

    /** Running text. */
    val body = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    /** The line under a heading. */
    val label = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )

    /**
     * Technical annotation: units, protocol names, group captions.
     *
     * Wide tracking is what makes it read as a label rather than as small body
     * text. Callers apply uppercase themselves — a string that has to be
     * uppercased in code cannot be translated into a language where that is
     * wrong.
     */
    val tech = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.88.sp, // 0.08em at 11sp
    )

    /** A number inside a list: latency, counts. */
    val numInline = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )

    /** A log line: the tech face at reading size. */
    val log = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
    )
}

/**
 * Material's scale, mapped onto the tokens above.
 *
 * Kept because Material components (dialogs, menus, snackbars) read it. Screens
 * use [StarveilType] directly — this exists so a component that is not ours
 * still lands on the same scale instead of Material's defaults.
 */
val StarveilTypography = Typography(
    displayLarge = StarveilType.display,
    displayMedium = StarveilType.ringWord,
    titleLarge = StarveilType.title,
    titleMedium = StarveilType.headline,
    bodyLarge = StarveilType.body,
    bodyMedium = StarveilType.label,
    labelLarge = StarveilType.headline,
    labelMedium = StarveilType.label,
    labelSmall = StarveilType.tech,
)