package com.justnpcx.starveil.core.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Starveil HUD design system — "deep space black + cyan-blue glow".
 *
 * Dark-first: the dark scheme is the primary identity. Light is a fallback,
 * not the design target.
 */
object StarveilColors {
    /** Deep-space black background. */
    val Void = Color(0xFF050810)

    /** Panel surface, one step above Void. */
    val Panel = Color(0xFF0B101D)

    /** Raised surface (cards, sheets). */
    val Raised = Color(0xFF121A2E)

    /** Primary glow: cyan-blue. */
    val GlowPrimary = Color(0xFF38BDF8)

    /** Secondary glow: indigo. */
    val GlowSecondary = Color(0xFF818CF8)

    /** Connected / healthy. */
    val Connected = Color(0xFF34D399)

    /** Connecting / working. */
    val Connecting = Color(0xFF38BDF8)

    /** Blocked / alert. */
    val Blocked = Color(0xFFF87171)

    /**
     * Disconnected: dimmed, but deliberately not the disabled colour — a
     * disconnected tunnel is a normal state, not an unavailable control.
     */
    val Disconnected = Color(0xFF64748B)

    /** Muted text on dark. */
    val TextDim = Color(0xFF94A3B8)

    // --- Chart series (design system §2.1) -------------------------------
    // The sparkline had been borrowing the glow colours, which happen to be
    // the same values today but mean something different: a chart series is
    // not a glow, and changing one must not silently change the other.

    /** Chart line 1 — downlink. */
    val Chart1 = Color(0xFF38BDF8)

    /** Chart line 2 — uplink. */
    val Chart2 = Color(0xFF818CF8)

    /** Chart grid line. */
    val ChartGrid = Color(0xFF1E293B)
}

private val DarkScheme = darkColorScheme(
    primary = StarveilColors.GlowPrimary,
    secondary = StarveilColors.GlowSecondary,
    tertiary = StarveilColors.Connected,
    background = StarveilColors.Void,
    surface = StarveilColors.Panel,
    surfaceVariant = StarveilColors.Raised,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = StarveilColors.TextDim,
    error = StarveilColors.Blocked,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0369A1),
    secondary = Color(0xFF4F46E5),
    tertiary = Color(0xFF047857),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B),
    error = Color(0xFFDC2626),
)

/**
 * @param darkTheme dark-first: defaults to dark regardless of the system. The
 *   caller derives this from the stored theme mode.
 * @param dynamicColor when true and the platform supports it (API 31+), take
 *   the palette from the wallpaper instead of the Starveil HUD scheme. Off by
 *   default — the HUD identity is the design target, dynamic colour is an
 *   opt-in that deliberately overrides it.
 */
@Composable
fun StarveilTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = StarveilTypography,
        content = content,
    )
}
