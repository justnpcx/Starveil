package com.justnpcx.starveil.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette in force, as a CompositionLocal.
 *
 * A local rather than a global object because there are two palettes and the
 * theme decides which. Screens read `StarveilColors.ink`, which resolves through
 * here — so a screen never asks which theme it is in, and a page cannot end up
 * with one design for light and another for dark.
 */
val LocalStarveilPalette = staticCompositionLocalOf { LightPalette }

/**
 * Every colour the app is allowed to use.
 *
 * Names are roles: `ink` is "primary text", not "the colour #1A1815". The
 * values come from [LocalStarveilPalette], so the same call means the light
 * value on a light page and the dark value on a dark one.
 *
 * There is no `Border`, `Glow`, or `Chart1..5` any more. Those existed to give
 * the HUD its cyan edges and its five-series sparkline; this design has one
 * divider, one accent, and three status colours, and a token nobody should use
 * is a token somebody will.
 */
object StarveilColors {
    /** Page background. */
    val bg: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.bg

    /** A surface that floats over the page: sheets, dialogs. */
    val surface: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.surface

    /** Primary text. */
    val ink: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink

    /** Secondary text: labels, descriptions. */
    val ink2: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink2

    /** Tertiary: placeholders, timestamps, disabled values. */
    val ink3: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink3

    /** The one divider colour. */
    val hairline: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.hairline

    /** Champagne gold. Lines and dots only — never a fill. */
    val gold: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.gold

    /** Gold at text weight. */
    val goldDeep: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.goldDeep

    /** The only permitted wash: a selected row. */
    val goldWash: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.goldWash

    /** Healthy. */
    val ok: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ok

    /** Attention. */
    val warn: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.warn

    /** Failure, blocking. */
    val err: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.err
    // --- Temporary bridge, removed once every screen is migrated ------------
    //
    // The old names are still referenced by the screens that have not been
    // reskinned yet. They map onto the new roles so an intermediate commit
    // builds; each one disappears with the screen that used it, and the block
    // goes with the last.

    /** @deprecated the page background. */
    val Void: Color @Composable @ReadOnlyComposable get() = bg

    /** @deprecated the page background. */
    val Panel: Color @Composable @ReadOnlyComposable get() = bg

    /** @deprecated a floating surface. */
    val Raised: Color @Composable @ReadOnlyComposable get() = surface

    /** @deprecated a floating surface. */
    val Snackbar: Color @Composable @ReadOnlyComposable get() = surface

    /** @deprecated the accent. */
    val GlowPrimary: Color @Composable @ReadOnlyComposable get() = gold

    /** @deprecated the accent. */
    val GlowSecondary: Color @Composable @ReadOnlyComposable get() = goldDeep

    /** @deprecated healthy. */
    val Connected: Color @Composable @ReadOnlyComposable get() = ok

    /** @deprecated the accent. */
    val Connecting: Color @Composable @ReadOnlyComposable get() = gold

    /** @deprecated failure. */
    val Blocked: Color @Composable @ReadOnlyComposable get() = err

    /** @deprecated attention. */
    val Warning: Color @Composable @ReadOnlyComposable get() = warn

    /** @deprecated an idle value. */
    val Disconnected: Color @Composable @ReadOnlyComposable get() = ink3

    /** @deprecated secondary text. */
    val TextDim: Color @Composable @ReadOnlyComposable get() = ink2

    /** @deprecated tertiary text. */
    val TextDisabled: Color @Composable @ReadOnlyComposable get() = ink3

    /** @deprecated the divider. */
    val Border: Color @Composable @ReadOnlyComposable get() = hairline

    /** @deprecated the accent. */
    val BorderGlow: Color @Composable @ReadOnlyComposable get() = gold

    /** @deprecated the selected wash. */
    val GlowPrimaryDim: Color @Composable @ReadOnlyComposable get() = goldWash

    /** @deprecated one series. */
    val Chart1: Color @Composable @ReadOnlyComposable get() = gold

    /** @deprecated one series. */
    val Chart2: Color @Composable @ReadOnlyComposable get() = ink2

    /** @deprecated one series. */
    val Chart3: Color @Composable @ReadOnlyComposable get() = ok

    /** @deprecated one series. */
    val Chart4: Color @Composable @ReadOnlyComposable get() = warn

    /** @deprecated one series. */
    val Chart5: Color @Composable @ReadOnlyComposable get() = err

    /** @deprecated the divider. */
    val ChartGrid: Color @Composable @ReadOnlyComposable get() = hairline
}

/**
 * Material's scheme, derived from a palette.
 *
 * Only what Material components actually read is set: a dialog's container, a
 * menu's surface, a text field's cursor. `surfaceVariant` is deliberately the
 * page background rather than a raised tone — nothing in this design is a
 * filled block, so a component that reaches for it should disappear into the
 * page instead of drawing a card.
 */
private fun ColorScheme.withPalette(p: StarveilPalette): ColorScheme = copy(
    primary = p.ink,
    onPrimary = p.bg,
    secondary = p.goldDeep,
    onSecondary = p.bg,
    tertiary = p.ok,
    background = p.bg,
    onBackground = p.ink,
    surface = p.bg,
    onSurface = p.ink,
    surfaceVariant = p.bg,
    onSurfaceVariant = p.ink2,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.surface,
    surfaceContainerHighest = p.surface,
    outline = p.hairline,
    outlineVariant = p.hairline,
    error = p.err,
    onError = p.bg,
    // Snackbars float over the page, so they are the one place a surface is a
    // different colour from the page — otherwise the bar reads as a hole.
    inverseSurface = p.ink,
    inverseOnSurface = p.bg,
    inversePrimary = p.gold,
)

internal val DarkScheme = darkColorScheme().withPalette(DarkPalette)

internal val LightScheme = lightColorScheme().withPalette(LightPalette)

/**
 * @param darkTheme which palette. The caller derives it from the stored theme
 *   mode; this composable does not read the system setting itself, because
 *   "follow the system" is a stored choice and not the same thing as "dark".
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
            content = content,
        )
    }
}