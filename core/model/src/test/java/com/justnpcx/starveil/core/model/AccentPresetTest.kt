package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AccentPresetTest {

    @Test
    fun `the system preset round-trips through storage`() {
        assertEquals(
            AccentPreset.SYSTEM_DYNAMIC,
            AccentPreset.fromStorage(AccentPreset.SYSTEM_DYNAMIC.name),
        )
    }

    @Test
    fun `an unknown value falls back to the default`() {
        assertEquals(AccentPreset.STAR_BLUE, AccentPreset.fromStorage("SOLAR_FLARE"))
        assertEquals(AccentPreset.STAR_BLUE, AccentPreset.fromStorage(null))
    }

    @Test
    fun `the system preset is first and the default is unchanged`() {
        assertEquals(AccentPreset.SYSTEM_DYNAMIC, AccentPreset.entries.first())
        // A new install, or a value written by an older build, lands here.
        assertEquals(AccentPreset.STAR_BLUE, AccentPreset.fromStorage(null))
    }
}
