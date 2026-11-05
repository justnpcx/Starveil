package com.justnpcx.starveil.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.justnpcx.starveil.core.model.AccentPreset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one preset whose tone is injected rather than looked up.
 *
 * Two things have to hold: an injected tone wins, and a missing one falls back
 * to the default preset instead of throwing or half-theming the app. The second
 * is what an Android 11 phone and a unit test without a Context both rely on.
 */
class DynamicAccentTest {

    private val injected = AccentTone(
        accent = Color(0xFF33691E),
        selBg = Color(0xFFDCEDC8),
        selInk = Color(0xFF1B5E20),
    )

    @Test
    fun `an injected tone is the palette's accent family`() {
        val palette = paletteFor(dark = false, preset = AccentPreset.SYSTEM_DYNAMIC, dynamicTone = injected)

        assertEquals(injected.accent, palette.accent)
        assertEquals(injected.selBg, palette.selBg)
        assertEquals(injected.selInk, palette.selInk)
    }

    @Test
    fun `a missing tone falls back to the default preset`() {
        val palette = paletteFor(dark = false, preset = AccentPreset.SYSTEM_DYNAMIC, dynamicTone = null)
        val starBlue = paletteFor(dark = false, preset = AccentPreset.STAR_BLUE)

        assertEquals(starBlue.accent, palette.accent)
        assertEquals(starBlue.selBg, palette.selBg)
        assertEquals(starBlue.selInk, palette.selInk)
    }

    @Test
    fun `the fallback follows the mode`() {
        val darkPalette = paletteFor(dark = true, preset = AccentPreset.SYSTEM_DYNAMIC, dynamicTone = null)
        val starBlueDark = paletteFor(dark = true, preset = AccentPreset.STAR_BLUE)

        assertEquals(starBlueDark.accent, darkPalette.accent)
    }

    @Test
    fun `a fixed preset ignores the injected tone`() {
        val palette = paletteFor(dark = false, preset = AccentPreset.VIOLET, dynamicTone = injected)
        val violet = paletteFor(dark = false, preset = AccentPreset.VIOLET)

        assertEquals(violet.accent, palette.accent)
        assertEquals(violet.selBg, palette.selBg)
    }

    @Test
    fun `the swatch tone resolves the same way as the palette`() {
        assertEquals(injected, accentToneFor(dark = false, preset = AccentPreset.SYSTEM_DYNAMIC, dynamicTone = injected))
        assertEquals(
            accentToneFor(dark = false, preset = AccentPreset.STAR_BLUE),
            accentToneFor(dark = false, preset = AccentPreset.SYSTEM_DYNAMIC, dynamicTone = null),
        )
    }
}
