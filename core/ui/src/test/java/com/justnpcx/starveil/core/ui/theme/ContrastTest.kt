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

    /**
     * The 0.9.3 additions: the hub's four semantic tones, the switch's parts,
     * and the plus disc's face.
     *
     * Every pair here is a mark rather than running text — a glyph on its bed,
     * a thumb on its track — so the bar is the graphic threshold, 3:1.
     */
    @Test
    fun `the new marks clear the graphic threshold`() {
        for ((name, p) in palettes) {
            val tones = listOf(
                "hub scenes" to p.hubScenes,
                "hub logs" to p.hubLogs,
                "hub diagnostics" to p.hubDiagnostics,
                "hub settings" to p.hubSettings,
            )
            for ((what, tone) in tones) {
                assertAtLeast(3.0, tone.ink, tone.bg, name + ": " + what + " glyph on its bed")
            }
            assertAtLeast(3.0, p.swOut, p.swOff, name + ": switch thumb off on track off")
            assertAtLeast(3.0, p.swChk, p.swOnTh, name + ": switch check on thumb on")
            assertAtLeast(3.0, p.accent, p.accentSoft, name + ": accent glyph on accentSoft")
            // The disc's ring and glyph are drawn in the brand gradient; the
            // lightest stop is the one that has to hold up against the face.
            assertAtLeast(3.0, StarveilBrand.blue, p.floatDisc, name + ": gradient on disc face")
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
                "hub scenes glyph on bed" to (p.hubScenes.ink to p.hubScenes.bg),
                "hub logs glyph on bed" to (p.hubLogs.ink to p.hubLogs.bg),
                "hub diagnostics glyph on bed" to (p.hubDiagnostics.ink to p.hubDiagnostics.bg),
                "hub settings glyph on bed" to (p.hubSettings.ink to p.hubSettings.bg),
                "switch thumb off on track off" to (p.swOut to p.swOff),
                "switch check on thumb on" to (p.swChk to p.swOnTh),
                "accent on accentSoft" to (p.accent to p.accentSoft),
                "gradient blue on floatDisc" to (StarveilBrand.blue to p.floatDisc),
                "gradient pink on floatDisc" to (StarveilBrand.pink to p.floatDisc),
            )
            for ((what, colors) in pairs) {
                val key = "CONTRAST_" + name.uppercase() + "_" + what.replace(' ', '_')
                println(key + "=" + "%.2f".format(contrast(colors.first, colors.second)))
            }
        }
    }

    /**
     * Every accent preset, in both modes.
     *
     * A preset that cannot carry text on the page is a preset that has to be
     * adjusted, not shipped — the whole point of a short measured list is that
     * all five are usable.
     */
    @Test
    fun everyAccentPresetClearsTheTextThreshold() {
        for (preset in com.justnpcx.starveil.core.model.AccentPreset.entries) {
            for (dark in listOf(false, true)) {
                val p = paletteFor(dark, preset)
                val where = preset.name + (if (dark) " dark" else " light")
                assertAtLeast(4.5, p.accent, p.bg, where + ": accent on bg")
                assertAtLeast(4.5, p.accent, p.card, where + ": accent on card")
                assertAtLeast(4.5, p.selInk, p.selBg, where + ": selInk on selBg")
            }
        }
    }

    @Test
    fun reportEveryAccentPreset() {
        for (preset in com.justnpcx.starveil.core.model.AccentPreset.entries) {
            for (dark in listOf(false, true)) {
                val p = paletteFor(dark, preset)
                val where = "PRESET_" + preset.name + (if (dark) "_DARK" else "_LIGHT")
                println(where + "_accent_on_bg=" + "%.2f".format(contrast(p.accent, p.bg)))
                println(where + "_accent_on_card=" + "%.2f".format(contrast(p.accent, p.card)))
                println(where + "_selInk_on_selBg=" + "%.2f".format(contrast(p.selInk, p.selBg)))
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
