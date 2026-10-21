package com.justnpcx.starveil.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG contrast, measured rather than eyeballed.
 *
 * "Looks fine on my screen" is how a palette ends up with dim grey text on a
 * warm white page that disappears outdoors — and this design is built out of
 * text, hairlines and one accent, so text contrast is not a detail of it, it is
 * most of the interface.
 *
 * The numbers are the standard ones: 4.5:1 for body text (AA), 3:1 for large
 * text and for a status colour used as an indicator rather than as a word.
 *
 * Both palettes are held to the same floor. The light one is not a fallback any
 * more — it is the primary version — and the dark one is the same design lit
 * from the other side, so a value that only works in one of them is a value
 * that is wrong.
 */
class ContrastTest {

    private val palettes = listOf("light" to LightPalette, "dark" to DarkPalette)

    @Test
    fun `body text meets AA on the page`() {
        palettes.forEach { (name, p) ->
            assertAtLeast(4.5, p.ink, p.bg, "$name ink on bg")
            // ink2 is every description line in the app, so it is body text and
            // gets no large-text exemption.
            assertAtLeast(4.5, p.ink2, p.bg, "$name ink2 on bg")
        }
    }

    @Test
    fun `body text meets AA on a floating surface`() {
        // Sheets and dialogs are the only places a surface is not the page, and
        // they carry the same text.
        palettes.forEach { (name, p) ->
            assertAtLeast(4.5, p.ink, p.surface, "$name ink on surface")
            assertAtLeast(4.5, p.ink2, p.surface, "$name ink2 on surface")
        }
    }

    @Test
    fun `gold used as text meets AA`() {
        // goldDeep is the token for exactly this: a badge label or a value that
        // has to be readable. `gold` itself is a line colour and is not held to
        // the text floor — see the next test.
        palettes.forEach { (name, p) ->
            assertAtLeast(4.5, p.goldDeep, p.bg, "$name goldDeep on bg")
        }
    }

    @Test
    fun `gold used as a line clears the non-text threshold`() {
        // WCAG 1.4.11: a graphic that carries meaning needs 3:1 against what is
        // behind it. The ring and the selection dot are graphics.
        palettes.forEach { (name, p) ->
            assertAtLeast(3.0, p.gold, p.bg, "$name gold on bg")
        }
    }

    @Test
    fun `status colours are readable as text`() {
        // The availability percentage is drawn in its band's colour, and the
        // notice line uses these as text. Small labels, so 4.5:1.
        palettes.forEach { (name, p) ->
            assertAtLeast(4.5, p.ok, p.bg, "$name ok on bg")
            assertAtLeast(4.5, p.err, p.bg, "$name err on bg")
        }
    }

    @Test
    fun `amber is readable as text`() {
        // Held apart from the other two because amber is the one that is easy to
        // get wrong: a bright amber reads as "healthy" and a dark one as brown.
        // Both palettes must clear AA with it.
        palettes.forEach { (name, p) ->
            assertAtLeast(4.5, p.warn, p.bg, "$name warn on bg")
        }
    }

    @Test
    fun `a selected row's text stays readable on its wash`() {
        // The wash is the one fill in the design, so it has to keep the row's
        // own text above AA — otherwise selecting a node would make it harder
        // to read, which is the opposite of what selection means.
        palettes.forEach { (name, p) ->
            assertAtLeast(4.5, p.ink, p.goldWash, "$name ink on goldWash")
            assertAtLeast(4.5, p.ink2, p.goldWash, "$name ink2 on goldWash")
        }
    }

    @Test
    fun `ink3 is allowed to be low contrast`() {
        // Recorded, not asserted: ink3 is placeholders and timestamps, which
        // WCAG treats leniently and which would stop reading as secondary if
        // they were darkened to AA. The number is here so a future change to it
        // is a deliberate one.
        palettes.forEach { (name, p) ->
            val ratio = contrast(p.ink3, p.bg)
            println("CONTRAST_INK3_${name.uppercase()}=${"%.2f".format(ratio)}")
            assertTrue("$name ink3 should still be visible at all", ratio > 1.8)
        }
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
}