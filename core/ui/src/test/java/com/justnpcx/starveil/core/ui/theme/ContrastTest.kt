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
 * black page that disappears outdoors — and this design is built out of text,
 * hairlines and one accent, so text contrast is not a detail of it, it is most
 * of the interface.
 *
 * The numbers are the standard ones: 4.5:1 for body text (AA), 3:1 for a
 * graphic that carries meaning.
 */
class ContrastTest {

    private val p = GlassPalette

    @Test
    fun `body text meets AA on the page`() {
        assertAtLeast(4.5, p.ink, p.bg, "ink on bg")
        // ink2 is every description line in the app, so it is body text and
        // gets no large-text exemption.
        assertAtLeast(4.5, p.ink2, p.bg, "ink2 on bg")
    }

    @Test
    fun `body text meets AA on a glass card`() {
        // A card is one step off the page and carries the same text.
        assertAtLeast(4.5, p.ink, p.surfaceGlass, "ink on surfaceGlass")
        assertAtLeast(4.5, p.ink2, p.surfaceGlass, "ink2 on surfaceGlass")
    }

    @Test
    fun `the accent is readable as text`() {
        // Text in the accent uses its text grade: #0071E3 is 4.01:1 on this
        // page, which is a mark's contrast, not a paragraph's.
        assertAtLeast(4.5, p.accentText, p.bg, "accentText on bg")
        assertAtLeast(4.5, p.accentText, p.surfaceGlass, "accentText on surfaceGlass")
    }

    @Test
    fun `the accent is a line before it is a fill`() {
        // WCAG 1.4.11: a graphic that carries meaning needs 3:1 against what is
        // behind it. The ring, the selected chip's edge and the navigation dot
        // are graphics.
        assertAtLeast(3.0, p.accent, p.bg, "accent on bg")
        assertAtLeast(3.0, p.err, p.bg, "err dot on bg")
    }

    @Test
    fun `status colours are readable as text`() {
        // The availability percentage is drawn in its band's colour, and the
        // notice line uses these as text. Small labels, so 4.5:1.
        assertAtLeast(4.5, p.ok, p.bg, "ok on bg")
        assertAtLeast(4.5, p.errText, p.bg, "errText on bg")
        assertAtLeast(4.5, p.warn, p.bg, "warn on bg")
    }

    @Test
    fun `a selected row's text stays readable on its wash`() {
        // The wash is the one tinted fill in the design (the accent at 12% over
        // the page), so it has to keep the row's own text above AA — otherwise
        // selecting a node would make it harder to read.
        val wash = blend(p.accent.copy(alpha = 0.10f), p.bg)
        assertAtLeast(4.5, p.ink, wash, "ink on the selected wash")
        assertAtLeast(4.5, p.ink2, wash, "ink2 on the selected wash")
    }

    @Test
    fun `ink3 is allowed to be low contrast`() {
        // Recorded, not asserted: ink3 is placeholders and timestamps, which
        // WCAG treats leniently and which would stop reading as secondary if
        // they were lifted to AA. The number is here so a future change to it
        // is a deliberate one.
        val ratio = contrast(p.ink3, p.bg)
        println("CONTRAST_INK3=${"%.2f".format(ratio)}")
        assertTrue("ink3 should still be visible at all", ratio > 1.8)
    }

    /** [foreground] at [alpha] composited over [background]. */
    private fun blend(foreground: Color, background: Color): Color = Color(
        red = foreground.red * foreground.alpha + background.red * (1 - foreground.alpha),
        green = foreground.green * foreground.alpha + background.green * (1 - foreground.alpha),
        blue = foreground.blue * foreground.alpha + background.blue * (1 - foreground.alpha),
    )

    private fun assertAtLeast(minimum: Double, foreground: Color, background: Color, what: String) {
        val ratio = contrast(foreground, background)
        assertTrue(
            "$what: ${"%.2f".format(ratio)}:1 is below $minimum:1 " +
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
