package com.justnpcx.starveil.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG contrast, measured rather than eyeballed.
 *
 * The task book's section 10 list, in both modes. Every pair is body text, so
 * the threshold is 4.5:1; the graphic checks are 3:1.
 */
class ContrastTest {

    private val palettes = listOf("light" to LightPalette, "dark" to DarkPalette)

    @Test
    fun `body text meets AA on the page and on a card`() {
        for ((name, p) in palettes) {
            assertAtLeast(4.5, p.ink, p.bg, name + ": ink on bg")
            assertAtLeast(4.5, p.ink, p.card, name + ": ink on card")
            assertAtLeast(4.5, p.ink2, p.bg, name + ": ink2 on bg")
            assertAtLeast(4.5, p.ink2, p.card, name + ": ink2 on card")
        }
    }

    @Test
    fun `the accent is readable as text`() {
        for ((name, p) in palettes) {
            assertAtLeast(4.5, p.accent, p.bg, name + ": accent on bg")
            assertAtLeast(4.5, p.accent, p.card, name + ": accent on card")
        }
    }

    @Test
    fun `selected text is readable on the selected fill`() {
        for ((name, p) in palettes) {
            assertAtLeast(4.5, p.selInk, p.selBg, name + ": selInk on selBg")
        }
    }

    @Test
    fun `status colours are readable as text`() {
        for ((name, p) in palettes) {
            assertAtLeast(4.5, p.err, p.card, name + ": err on card")
            assertAtLeast(4.5, p.warn, p.bg, name + ": warn on bg")
            assertAtLeast(4.5, p.warn, p.card, name + ": warn on card")
            assertAtLeast(4.5, p.ok, p.bg, name + ": ok on bg")
        }
    }

    @Test
    fun `marks clear the graphic threshold`() {
        for ((name, p) in palettes) {
            assertAtLeast(3.0, p.accent, p.bg, name + ": accent mark on bg")
            assertAtLeast(3.0, p.err, p.bg, name + ": err dot on bg")
        }
    }

    @Test
    fun `report every measured ratio`() {
        for ((name, p) in palettes) {
            val pairs = listOf(
                "ink on bg" to (p.ink to p.bg),
                "ink on card" to (p.ink to p.card),
                "ink2 on bg" to (p.ink2 to p.bg),
                "ink2 on card" to (p.ink2 to p.card),
                "accent on bg" to (p.accent to p.bg),
                "selInk on selBg" to (p.selInk to p.selBg),
                "err on card" to (p.err to p.card),
                "warn on bg" to (p.warn to p.bg),
                "warn on card" to (p.warn to p.card),
            )
            for ((what, colors) in pairs) {
                val key = "CONTRAST_" + name.uppercase() + "_" + what.replace(' ', '_')
                println(key + "=" + "%.2f".format(contrast(colors.first, colors.second)))
            }
        }
    }

    private fun assertAtLeast(minimum: Double, foreground: Color, background: Color, what: String) {
        val ratio = contrast(foreground, background)
        assertTrue(
            what + ": " + "%.2f".format(ratio) + ":1 is below " + minimum + ":1 " +
                "(fg=" + foreground.hex() + ", bg=" + background.hex() + ")",
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
