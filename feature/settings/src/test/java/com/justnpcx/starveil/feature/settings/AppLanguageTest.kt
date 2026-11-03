package com.justnpcx.starveil.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which row the language page ticks for a given tag.
 *
 * The bug this guards against is not visible in one locale: the page used to
 * compare `Locale.getLanguage()`, which is "zh" for both Chinese rows, so a
 * simplified user saw the traditional row ticked too as soon as it existed.
 * A test is the only way to check five system locales without five devices.
 */
class AppLanguageTest {

    @Test
    fun `simplified tags tick only the simplified row`() {
        assertEquals(AppLanguage.CHINESE, selectedLanguage("zh-CN"))
        assertEquals(AppLanguage.CHINESE, selectedLanguage("zh-CN-x-private"))
        assertEquals(AppLanguage.CHINESE, selectedLanguage("zh-Hans-CN"))
    }

    @Test
    fun `traditional tags tick only the traditional row`() {
        assertEquals(AppLanguage.TRADITIONAL, selectedLanguage("zh-TW"))
        assertEquals(AppLanguage.TRADITIONAL, selectedLanguage("zh-Hant-HK"))
        assertEquals(AppLanguage.TRADITIONAL, selectedLanguage("zh-HK"))
        assertEquals(AppLanguage.TRADITIONAL, selectedLanguage("zh-MO"))
    }

    @Test
    fun `english ticks only the english row`() {
        assertEquals(AppLanguage.ENGLISH, selectedLanguage("en"))
        assertEquals(AppLanguage.ENGLISH, selectedLanguage("en-GB"))
    }

    @Test
    fun `an empty tag means follow the system`() {
        assertEquals(AppLanguage.SYSTEM, selectedLanguage(null))
        assertEquals(AppLanguage.SYSTEM, selectedLanguage(""))
    }

    @Test
    fun `one tag ticks exactly one row`() {
        val tags = listOf("zh-CN", "zh-TW", "zh-Hant-HK", "en", "")
        for (tag in tags) {
            val ticked = AppLanguage.entries.filter { it == selectedLanguage(tag) }
            assertEquals("tag=$tag", 1, ticked.size)
        }
        // A language the app does not ship leaves every row unticked rather
        // than claiming the system row, which would read as "follow the
        // device" while the device is in a language the page cannot offer.
        assertNull(selectedLanguage("de-DE"))
    }
}
