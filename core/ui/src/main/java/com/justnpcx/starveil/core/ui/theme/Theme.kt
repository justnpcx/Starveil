package com.justnpcx.starveil.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The palette in force, as a CompositionLocal.
 *
 * A local rather than a global object so Material's own components resolve the
 * same values as ours. There is exactly one palette; a screen never asks which
 * theme it is in, because there is no question to ask.
 */
val LocalStarveilPalette = staticCompositionLocalOf { ObsidianPalette }

/**
 * Every colour the app is allowed to use.
 *
 * Names are roles: `ink` is "primary text", not "the colour #F5F7FA".
 */
object StarveilColors {
    /** Page background. Pure black. */
    val bg: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.bg

    /** A glass card, a sheet. */
    val surfaceGlass: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.surfaceGlass

    /** The 1dp edge of a glass card. */
    val glassBorder: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.glassBorder

    /** Dividers inside a card, and the line above the navigation bar. */
    val hairline: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.hairline

    /** Primary text. */
    val ink: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink

    /** Secondary text: labels, descriptions. */
    val ink2: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink2

    /** Tertiary: placeholders, timestamps, unselected icons. */
    val ink3: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink3

    /** The gradient's start: electric cyan. */
    val accentA: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.accentA

    /** The gradient's end: violet. */
    val accentB: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.accentB

    /** Online, healthy. */
    val ok: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ok

    /** Attention. */
    val warn: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.warn

    /** Failure, blocking. */
    val err: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.err

    /** The ring at rest. */
    val ringIdle: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ringIdle

    /** A selected row's wash: the accent at 12%, the only tinted fill allowed. */
    val accentWash: Color
        @Composable @ReadOnlyComposable get() = accentA.copy(alpha = 0.12f)

}

/**
 * Material's scheme, derived from the palette.
 *
 * Only what Material components actually read is set: a dialog's container, a
 * menu's surface, a text field's cursor. `surfaceVariant` is deliberately the
 * page background rather than a raised tone — nothing in this design is a
 * filled block, so a component that reaches for it should disappear into the
 * page instead of drawing a card.
 */
private fun ColorScheme.withPalette(p: StarveilPalette): ColorScheme = copy(
    primary = p.accentA,
    onPrimary = p.bg,
    secondary = p.accentB,
    onSecondary = p.bg,
    tertiary = p.ok,
    background = p.bg,
    onBackground = p.ink,
    surface = p.bg,
    onSurface = p.ink,
    surfaceVariant = p.bg,
    onSurfaceVariant = p.ink2,
    surfaceContainer = p.surfaceGlass,
    surfaceContainerHigh = p.surfaceGlass,
    surfaceContainerHighest = p.surfaceGlass,
    surfaceContainerLow = p.surfaceGlass,
    outline = p.glassBorder,
    outlineVariant = p.hairline,
    error = p.err,
    onError = p.bg,
    // Snackbars float over the page, so they are the one place a surface is a
    // different colour from the page — otherwise the bar reads as a hole.
    inverseSurface = p.ink,
    inverseOnSurface = p.bg,
    inversePrimary = p.accentA,
)

internal val ObsidianScheme = darkColorScheme().withPalette(ObsidianPalette)

/**
 * The app's theme. There are no arguments: one palette, one scheme.
 *
 * The absence is deliberate. A `darkTheme` parameter is the hook a second
 * theme hangs from, and this design has one.
 */
@Composable
fun StarveilTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStarveilPalette provides ObsidianPalette) {
        MaterialTheme(
            colorScheme = ObsidianScheme,
            typography = StarveilTypography,
            shapes = StarveilShapes.material,
            content = content,
        )
    }
}

/**
 * The radii the design uses, as tokens.
 *
 * A button and a chip are capsules; an input is 12dp; a card and a sheet are
 * 20dp. Three numbers, not a per-call decision.
 */
object StarveilShapes {

    /** A glass card. */
    val card = RoundedCornerShape(20.dp)

    /** A sheet: rounded at the top, square where it meets the screen edge. */
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** A text field. */
    val field = RoundedCornerShape(12.dp)

    /** Material's slots, so a stock component lands on the same corners. */
    val material = androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = field,
        medium = card,
        large = card,
        extraLarge = card,
    )
}
