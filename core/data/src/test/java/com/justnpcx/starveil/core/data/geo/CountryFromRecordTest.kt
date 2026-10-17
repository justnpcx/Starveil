package com.justnpcx.starveil.core.data.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The part of the lookup that has rules in it, tested without the database.
 *
 * The records are real: `ip2region` returns `国家|省|市|ISP|ISO`, and the three
 * cases that matter are a plain country, the SARs (which it files under CN) and
 * a reserved range.
 */
class CountryFromRecordTest {

    @Test
    fun `a plain record ends with its ISO code`() {
        assertEquals("US", countryFromRecord("United States|California|0|Google LLC|US"))
        assertEquals("JP", countryFromRecord("Japan|Tokyo|Akiruno|0|JP"))
        assertEquals("AU", countryFromRecord("Australia|Queensland|Brisbane|0|AU"))
    }

    @Test
    fun `hong kong is split out of china`() {
        assertEquals("HK", countryFromRecord("中国|香港特别行政区|0|0|CN"))
    }

    @Test
    fun `macao and taiwan are split out too`() {
        assertEquals("MO", countryFromRecord("中国|澳门特别行政区|0|0|CN"))
        assertEquals("TW", countryFromRecord("中国|台湾省|台北市|0|CN"))
    }

    @Test
    fun `mainland china stays CN`() {
        assertEquals("CN", countryFromRecord("中国|广东省|深圳市|电信|CN"))
    }

    @Test
    fun `a reserved range has no country`() {
        // 203.0.113.0/24 is documentation space; the record says Reserved and
        // the last field is not an ISO code.
        assertNull(countryFromRecord("Reserved|Reserved|Reserved|0|0"))
    }

    @Test
    fun `a malformed record is not a country`() {
        assertNull(countryFromRecord(""))
        assertNull(countryFromRecord("nonsense"))
        assertNull(countryFromRecord("United States|California|0|Google LLC|USA"))
    }
}
