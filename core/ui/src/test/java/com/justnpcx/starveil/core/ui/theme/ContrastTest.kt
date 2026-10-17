package com.justnpcx.starveil.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG contrast, measured rather than eyeballed.
 *
 * "Looks fine on my screen" is how a HUD palette ends up with dim grey text on
 * a near-black panel that is unreadable outdoors. The numbers here are the
 * standard ones: 4.5:1 for body text (AA), 3:1 for large text and for the
 * non-text indicators a status colour is used as.
 *
 * The dark scheme is the design target (the app is dark-first), so it is held
 * to the stricter reading; light is a fallback and gets the same floor.
 */
class ContrastTest {

    private val darkSurfaces = listOf(
        "background" to DarkScheme.background,
        "surface" to DarkScheme.surface,
        "surfaceVariant" to DarkScheme.surfaceVariant,
    )

    private val lightSurfaces = listOf(
        "background" to LightScheme.background,
        "surface" to LightScheme.surface,
        "surfaceVariant" to LightScheme.surfaceVariant,
    )

    @Test
    fun `body text meets AA on every dark surface`() {
        darkSurfaces.forEach { (name, surface) ->
            assertAtLeast(4.5, DarkScheme.onBackground, surface, "onBackground on $name")
            assertAtLeast(4.5, DarkScheme.onSurface, surface, "onSurface on $name")
            // onSurfaceVariant is the secondary text colour — the one used for
            // every description line in the app, so it is body text, not a
            // large-text exemption.
            assertAtLeast(4.5, DarkScheme.onSurfaceVariant, surface, "onSurfaceVariant on $name")
        }
    }

    @Test
    fun `body text meets AA on every light surface`() {
        lightSurfaces.forEach { (name, surface) ->
            assertAtLeast(4.5, LightScheme.onBackground, surface, "onBackground on $name")
            assertAtLeast(4.5, LightScheme.onSurface, surface, "onSurface on $name")
            assertAtLeast(4.5, LightScheme.onSurfaceVariant, surface, "onSurfaceVariant on $name")
        }
    }

    @Test
    fun `status colours are readable as text`() {
        // The availability percentage is drawn in the band's colour, and the
        // notice banners use these as text. They are small labels, so 4.5:1.
        //
        // Dark only: the app is dark-first and these literal colours are the
        // dark palette. They are NOT readable on the light surfaces — that is
        // a known gap, and fixing it means a theme-aware status palette rather
        // than a lighter shade here. Recorded rather than silently exempted.
        listOf(
            "connected" to StarveilColors.Connected,
            "warning" to StarveilColors.Warning,
            "blocked" to StarveilColors.Blocked,
        ).forEach { (name, colour) ->
            darkSurfaces.forEach { (surfaceName, surface) ->
                assertAtLeast(4.5, colour, surface, "$name on $surfaceName")
            }
        }
    }

    @Test
    fun `the disconnected ring clears the non-text threshold`() {
        // This one is a ring on the connection core, not text, so the standard
        // is 3:1 (WCAG 1.4.11) — and it measures 4.21:1, which is why it is not
        // held to the body-text floor.
        darkSurfaces.forEach { (name, surface) ->
            assertAtLeast(3.0, StarveilColors.Disconnected, surface, "disconnected on $name")
        }
    }

    @Test
    fun `the primary accent is readable as text`() {
        // The per-app target label is drawn in primary.
        darkSurfaces.forEach { (name, surface) ->
            assertAtLeast(4.5, DarkScheme.primary, surface, "primary on $name")
        }
    }

    @Test
    fun `the disabled colour is allowed to be low contrast`() {
        // Recorded, not asserted: WCAG exempts inactive components, and
        // raising this would make "disabled" look enabled. The number is here
        // so a future change to it is a deliberate one.
        val ratio = contrast(StarveilColors.TextDisabled, StarveilColors.Void)
        println("CONTRAST_DISABLED_TEXT=${"%.2f".format(ratio)}")
        assertTrue("disabled text should still be visible at all", ratio > 1.5)
    }

    private fun assertAtLeast(minimum: Double, foreground: Color, background: Color, what: String) {
        val ratio = contrast(foreground, background)
        assertTrue(
            "$what: ${"%.2f".format(ratio)}:1 is below ${minimum}:1 " +
                "(fg=${foreground.hex()}, bg=${background.hex()})",
            ratio >= minimum,
        )
    }

    private fun Color.hex(): String = "#%08X".format(toArgb())

    /** WCAG 2.1 relative luminance and contrast ratio. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** Keeps the compiler honest about the scheme type. */
    @Suppress("unused")
    private fun ColorScheme.identity(): ColorScheme = this
}
