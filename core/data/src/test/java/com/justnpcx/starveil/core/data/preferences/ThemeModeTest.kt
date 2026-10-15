package com.justnpcx.starveil.core.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored theme mode and the system setting are two different things; this
 * is the mapping between them. It was stored but never read until the theme
 * wiring landed, so the mapping is the only part worth pinning down.
 */
class ThemeModeTest {

    @Test
    fun `system follows the system setting`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun `dark overrides a light system`() {
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = true))
    }

    @Test
    fun `light overrides a dark system`() {
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = false))
    }
}
