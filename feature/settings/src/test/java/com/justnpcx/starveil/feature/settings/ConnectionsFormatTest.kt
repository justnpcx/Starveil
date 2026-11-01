package com.justnpcx.starveil.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The buckets a connection's age can fall into.
 *
 * Written as a test rather than checked on a device because the interesting
 * cases are the ones a device will not produce on demand: a connection open for
 * two days, and one whose start the kernel never reported.
 */
class ConnectionsFormatTest {

    @Test
    fun `a missing start is a dash`() {
        assertEquals("—", ConnectionsFormat.duration(null))
    }

    @Test
    fun `sub-second and sub-minute read as seconds`() {
        assertEquals("300ms", ConnectionsFormat.duration(300))
        assertEquals("12s", ConnectionsFormat.duration(12_000))
        assertEquals("59s", ConnectionsFormat.duration(59_999))
    }

    @Test
    fun `minutes and hours keep the coarser unit`() {
        assertEquals("1m 0s", ConnectionsFormat.duration(60_000))
        assertEquals("59m 59s", ConnectionsFormat.duration(3_599_000))
        assertEquals("1h 0m", ConnectionsFormat.duration(3_600_000))
        assertEquals("23h 59m", ConnectionsFormat.duration(86_340_000))
    }

    @Test
    fun `a day or more reads in days`() {
        assertEquals("1d 0h", ConnectionsFormat.duration(86_400_000))
        // The number the field reported: about thirty-nine years, which used to
        // render as "20891617m 43s".
        assertEquals("14506d 2h", ConnectionsFormat.duration(1_253_328_000_000L))
        assertEquals("2d 5h", ConnectionsFormat.duration(190_800_000L))
    }
}
