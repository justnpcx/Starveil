package com.justnpcx.starveil.feature.settings

import java.time.Instant
import java.time.ZoneId
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
    fun `a start the kernel never reported is a dash`() {
        // Rows inherited from a previous session arrive with 0, and the old
        // code formatted that as 08:00:00 — a wrong time that looked right.
        assertEquals("—", ConnectionsFormat.clock(0L, 1_700_000_000_000L))
        assertEquals("—", ConnectionsFormat.clock(-1L, 1_700_000_000_000L))
    }

    @Test
    fun `a start after now is a dash`() {
        val now = 1_700_000_000_000L
        // A start exactly now is not the future: it is a real start and gets a
        // real clock. Only a start after now has no wall-clock meaning.
        assertEquals("—", ConnectionsFormat.clock(now + 1, now))
        assertEquals(
            "22:13:20",
            ConnectionsFormat.clock(now, now, ZoneId.of("UTC")),
        )
    }

    @Test
    fun `a real start reads as a wall clock`() {
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2026-01-01T00:05:09Z").toEpochMilli()
        assertEquals(
            "00:05:09",
            ConnectionsFormat.clock(start, start + 60_000, zone),
        )
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
