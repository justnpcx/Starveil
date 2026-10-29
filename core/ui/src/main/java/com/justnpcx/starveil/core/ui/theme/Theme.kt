package com.justnpcx.starveil.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The palette in force, as a CompositionLocal. */
val LocalStarveilPalette = staticCompositionLocalOf { LightPalette }

/** The brand gradient. Shared by both modes and never re-tinted. */
object StarveilBrand {
    val blue = Color(0xFF4285F4)
    val violet = Color(0xFF7C6CF0)
    val pink = Color(0xFFE85D9E)

    /** 135 degrees, blue to violet at 55%, then pink. */
    val stops = arrayOf(0f to blue, 0.55f to violet, 1f to pink)
    val colors = listOf(blue, violet, pink)
}

/**
 * Every colour the app is allowed to use.
 *
 * Names are roles: `ink` is "primary text", not "the colour #1F1F1F".
 */
object StarveilColors {
    /** True in the dark table. Components branch on this only for shadows. */
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.dark

    val bg: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.bg
    val card: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.card
    val ink: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink
    val ink2: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink2
    val line: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.line
    val chipB: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.chipB
    val accent: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.accent
    val selBg: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.selBg
    val selInk: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.selInk
    val segBg: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.segBg
    val drawer: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.drawer
    val err: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.err
    val ok: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ok
    val warn: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.warn
    val ringOff: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ringOff
    val veil: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.veil
    val washes: List<Color>
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.washes
    val shadowTint: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.shadowTint

    // ---- Legacy names, each one an alias of a role above -------------------

    val surfaceGlass: Color
        @Composable @ReadOnlyComposable get() = card
    val surfaceThin: Color
        @Composable @ReadOnlyComposable get() = segBg
    val surfaceChrome: Color
        @Composable @ReadOnlyComposable get() = drawer
    val surfaceSolid: Color
        @Composable @ReadOnlyComposable get() = card
    val glassBorder: Color
        @Composable @ReadOnlyComposable get() = line
    val hairline: Color
        @Composable @ReadOnlyComposable get() = line
    val divider: Color
        @Composable @ReadOnlyComposable get() = line
    val ink3: Color
        @Composable @ReadOnlyComposable get() = ink2
    val accentText: Color
        @Composable @ReadOnlyComposable get() = accent
    val errText: Color
        @Composable @ReadOnlyComposable get() = err
    val ringIdle: Color
        @Composable @ReadOnlyComposable get() = ringOff
    val accentWash: Color
        @Composable @ReadOnlyComposable get() = selBg
    val curveFillAlpha: Float
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.curveFillAlpha
    val bgTop: Color
        @Composable @ReadOnlyComposable get() = bg
    val bgMid: Color
        @Composable @ReadOnlyComposable get() = bg
    val bgBottom: Color
        @Composable @ReadOnlyComposable get() = bg
}

/**
 * Material's scheme, from one palette.
 *
 * Every role in the task book's §9.1 list is written out, including the three
 * tonal containers that used to fall back to Material's lavender baseline.
 * There is no "leave it to the default" here: a role that is not listed is a
 * role a future component can paint purple.
 */
private fun StarveilPalette.scheme(base: ColorScheme): ColorScheme {
    val onAccent = if (dark) Color(0xFF00325B) else Color.White
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = selBg,
        onPrimaryContainer = selInk,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = selBg,
        onSecondaryContainer = selInk,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = selBg,
        onTertiaryContainer = selInk,
        background = bg,
        onBackground = ink,
        surface = card,
        onSurface = ink,
        surfaceVariant = segBg,
        onSurfaceVariant = ink2,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        surfaceBright = card,
        surfaceDim = bg,
        outline = chipB,
        outlineVariant = line,
        error = err,
        onError = if (dark) Color(0xFF3B0907) else Color.White,
        errorContainer = card,
        onErrorContainer = err,
        scrim = Color.Black,
        inverseSurface = ink,
        inverseOnSurface = card,
        inversePrimary = accent,
        // A raised surface is a shadow, never a tint.
        surfaceTint = Color.Transparent,
        // The fixed roles are containers under another name; unset they fall
        // back to the baseline scheme.
        primaryFixed = selBg,
        primaryFixedDim = selBg,
        onPrimaryFixed = selInk,
        onPrimaryFixedVariant = selInk,
        secondaryFixed = selBg,
        secondaryFixedDim = selBg,
        onSecondaryFixed = selInk,
        onSecondaryFixedVariant = selInk,
        tertiaryFixed = selBg,
        tertiaryFixedDim = selBg,
        onTertiaryFixed = selInk,
        onTertiaryFixedVariant = selInk,
    )
}

internal val LightScheme = LightPalette.scheme(lightColorScheme())
internal val DarkScheme = DarkPalette.scheme(darkColorScheme())

/**
 * The app's theme.
 *
 * [darkTheme] is decided once, in :app, from the persisted appearance choice.
 * No screen asks which mode it is in; it reads the same roles either way.
 */
@Composable
fun StarveilTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalStarveilPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = StarveilTypography,
            shapes = StarveilShapes.material,
            content = content,
        )
    }
}

/**
 * The radii the design uses, as tokens.
 */
object StarveilShapes {

    /** A card. */
    val card = RoundedCornerShape(22.dp)

    /** A dialog. */
    val dialog = RoundedCornerShape(28.dp)

    /** A sheet: rounded at the top, square where it meets the screen edge. */
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** A dialog's text input. */
    val field = RoundedCornerShape(12.dp)

    /** The search field: a capsule. */
    val search = RoundedCornerShape(percent = 50)

    /** The FAB: 56dp square with 16dp corners. */
    val fab = RoundedCornerShape(16.dp)

    /** Material's slots, so a stock component lands on the same corners. */
    val material = androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = field,
        medium = card,
        large = card,
        extraLarge = dialog,
    )
}
