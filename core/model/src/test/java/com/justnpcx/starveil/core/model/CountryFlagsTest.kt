package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryFlagsTest {

    @Test
    fun `a code becomes its flag`() {
        assertEquals("🇺🇸", CountryFlags.flag("US"))
        assertEquals("🇭🇰", CountryFlags.flag("hk"))
        assertEquals("🇯🇵", CountryFlags.flag("JP"))
    }

    @Test
    fun `anything that is not a two letter code has no flag`() {
        assertNull(CountryFlags.flag(null))
        assertNull(CountryFlags.flag(""))
        assertNull(CountryFlags.flag("USA"))
        assertNull(CountryFlags.flag("U1"))
    }

    @Test
    fun `the display name carries the flag only when the country is known`() {
        val known = Profile(
            name = "HY2+Reality",
            type = ProfileType.VLESS,
            server = "api.example.com",
            port = 443,
            country = "US",
        )
        assertEquals("🇺🇸 HY2+Reality", known.displayName())

        assertEquals("HY2+Reality", known.copy(country = null).displayName())
    }
}
