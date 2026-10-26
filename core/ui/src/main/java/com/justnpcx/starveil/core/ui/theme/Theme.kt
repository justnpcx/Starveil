package com.justnpcx.starveil.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
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
val LocalStarveilPalette = staticCompositionLocalOf { GlassPalette }

/**
 * Every colour the app is allowed to use.
 *
 * Names are roles: `ink` is "primary text", not "the colour #1D1D1F".
 */
object StarveilColors {
    /** The page's flat base, behind the ambient gradient. */
    val bg: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.bg

    /** The ambient gradient's three stops. */
    val bgTop: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.bgTop
    val bgMid: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.bgMid
    val bgBottom: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.bgBottom

    /** The three ambient washes painted over the gradient. */
    val washes: List<Color>
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.washes

    /** A glass card, a sheet. */
    val surfaceGlass: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.surfaceGlass

    /** Thinner material: inputs, flag chips, small wells. */
    val surfaceThin: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.surfaceThin

    /** The navigation bar's material. */
    val surfaceChrome: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.surfaceChrome

    /** The material with the transparency resolved, for anything over a scrim. */
    val surfaceSolid: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.surfaceSolid

    /** The 1dp edge of a glass card. */
    val glassBorder: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.glassBorder

    /** The 1dp highlight edge of a glass card. */
    val hairline: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.hairline

    /** A rule inside a card: ink at 10%, so it is darker than the card. */
    val divider: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.divider

    /** Primary text. */
    val ink: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink

    /** Secondary text: labels, descriptions. */
    val ink2: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink2

    /** Tertiary: placeholders, timestamps, unselected icons. */
    val ink3: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ink3

    /** The one accent: marks, switches, dots. */
    val accent: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.accent

    /** The accent as text. */
    val accentText: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.accentText

    /** Online, healthy — as text. */
    val ok: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.ok

    /** Attention. */
    val warn: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.warn

    /** Failure, as a dot or a line. */
    val err: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.err

    /** Failure as text. */
    val errText: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.errText

    /** The ring at rest: the tertiary grey, so an idle circle still has a shape. */
    val ringIdle: Color
        @Composable @ReadOnlyComposable get() = ink3

    /** A selected row's wash: the accent at 10%, the only tinted fill allowed. */
    val accentWash: Color
        @Composable @ReadOnlyComposable get() = accent.copy(alpha = 0.10f)

    /** The tint every shadow is drawn in. */
    val shadowTint: Color
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.shadowTint

    /** The health curve's fill alpha, under the line. */
    val curveFillAlpha: Float
        @Composable @ReadOnlyComposable get() = LocalStarveilPalette.current.curveFillAlpha
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
    primary = p.accent,
    onPrimary = Color.White,
    secondary = p.accent,
    onSecondary = Color.White,
    tertiary = p.ok,
    background = p.bg,
    onBackground = p.ink,
    surface = p.bg,
    onSurface = p.ink,
    surfaceVariant = p.bg,
    onSurfaceVariant = p.ink2,
    // Material's floating surfaces (dialogs, menus, sheets) sit over a dim
    // scrim, where a translucent material turns grey. They get the resolved
    // material instead.
    surfaceContainer = p.surfaceSolid,
    surfaceContainerHigh = p.surfaceSolid,
    surfaceContainerHighest = p.surfaceSolid,
    surfaceContainerLow = p.surfaceSolid,
    surfaceContainerLowest = p.surfaceSolid,
    surfaceBright = p.surfaceSolid,
    surfaceDim = p.bg,
    // Every tonal container, mapped explicitly. This palette has no tonal
    // steps: a container is the resolved material and its text is ink. Left
    // alone, Material fills them from its own baseline scheme, which is the
    // lavender one — that is how the scenes FAB came out pale purple while
    // every other surface in the app was blue. Mapping them here means a
    // Material component added later cannot reintroduce a second hue; it can
    // only look like the rest of the app.
    primaryContainer = p.surfaceSolid,
    onPrimaryContainer = p.ink,
    secondaryContainer = p.surfaceSolid,
    onSecondaryContainer = p.ink,
    tertiaryContainer = p.surfaceSolid,
    onTertiaryContainer = p.ink,
    errorContainer = p.surfaceSolid,
    onErrorContainer = p.errText,
    // The fixed roles are the same containers under another name; leaving them
    // unset would let a chip or a badge fall back to the baseline scheme.
    primaryFixed = p.surfaceSolid,
    primaryFixedDim = p.surfaceSolid,
    onPrimaryFixed = p.ink,
    onPrimaryFixedVariant = p.ink2,
    secondaryFixed = p.surfaceSolid,
    secondaryFixedDim = p.surfaceSolid,
    onSecondaryFixed = p.ink,
    onSecondaryFixedVariant = p.ink2,
    tertiaryFixed = p.surfaceSolid,
    tertiaryFixedDim = p.surfaceSolid,
    onTertiaryFixed = p.ink,
    onTertiaryFixedVariant = p.ink2,
    // No elevation tint: a raised surface in this design is the same material
    // with a shadow, not the accent mixed into it.
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
    outline = p.glassBorder,
    outlineVariant = p.divider,
    error = p.errText,
    onError = Color.White,
    // Snackbars float over the page, so they are the one place a surface is a
    // different colour from the page — otherwise the bar reads as a hole.
    inverseSurface = p.ink,
    inverseOnSurface = Color.White,
    inversePrimary = p.accent,
)

internal val GlassScheme = lightColorScheme().withPalette(GlassPalette)

/**
 * The app's theme. There are no arguments: one palette, one scheme.
 *
 * The absence is deliberate. A `darkTheme` parameter is the hook a second
 * theme hangs from, and this design has one.
 */
@Composable
fun StarveilTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStarveilPalette provides GlassPalette) {
        MaterialTheme(
            colorScheme = GlassScheme,
            typography = StarveilTypography,
            shapes = StarveilShapes.material,
            content = content,
        )
    }
}

/**
 * The radii the design uses, as tokens.
 *
 * A button and a chip are capsules; the search field is a capsule too, because
 * the demo draws it that way; a dialog's input is 12dp; a card is 22dp and a
 * sheet is 20dp at the top only. Five numbers, not a per-call decision.
 */
object StarveilShapes {

    /** A glass card. */
    val card = RoundedCornerShape(22.dp)

    /** A sheet: rounded at the top, square where it meets the screen edge. */
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** A dialog's text input. */
    val field = RoundedCornerShape(12.dp)

    /** The search field: a capsule. */
    val search = RoundedCornerShape(percent = 50)

    /** Material's slots, so a stock component lands on the same corners. */
    val material = androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = field,
        medium = card,
        large = card,
        extraLarge = card,
    )
}
