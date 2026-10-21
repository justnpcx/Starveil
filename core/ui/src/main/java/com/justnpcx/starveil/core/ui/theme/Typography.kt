package com.justnpcx.starveil.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type: one sans family, one mono for measurements.
 *
 * The display numbers are the sans at Light — a large thin numeral on black
 * reads as engraved rather than printed, and it is the whole reason the rates
 * look expensive. There is no serif anywhere: a serif on a black page with a
 * neon accent reads as a wine list, not as an instrument.
 *
 * The mono is the system's, so it costs nothing and needs no subsetting. It is
 * used only where a column of digits must not jitter, and for technical
 * annotations — units, protocol names, group captions.
 */

/** Technical annotation and tabular digits: system mono. */
val MonoFamily = FontFamily.Monospace

/** Everything that is read as language. */
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
        fontFamily = SansFamily,
        fontWeight = FontWeight.Light,
        fontSize = 54.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1).sp,
    )

    /** The word inside the ring. */
    val ringWord = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Light,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.5.sp,
    )

    /** A page's own title. */
    val title = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
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
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
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
 * Kept because Material components (dialogs, menus, snackbars, text fields)
 * read it. Screens use [StarveilType] directly — this exists so a component
 * that is not ours still lands on the same scale instead of Material's
 * defaults.
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
