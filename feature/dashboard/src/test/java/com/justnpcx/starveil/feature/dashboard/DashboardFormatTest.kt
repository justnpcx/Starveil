package com.justnpcx.starveil.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * §6.2's number formatting.
 *
 * The rule that matters is the carry: the unit is chosen from the *rounded*
 * value, so nothing is ever shown as 1023 of a unit when it is really 1 of the
 * next one. The design names that exact string as forbidden, and the old
 * implementation produced it by truncating.
 */
class DashboardFormatTest {

    @Test
    fun `a rate that rounds up is shown in the next unit`() {
        // 1023.6 KB/s. Integer division said "1023 KB/s"; it is 1.0 MB/s.
        assertEquals("1.0 MB/s", formatRate(1_048_166))
        // A hair under 1 GB/s: every unit in turn rounds up, so it lands on GB.
        assertEquals("1.0 GB/s", formatRate(1_073_741_724))
    }

    @Test
    fun `a rate that really is 1023 KB still says 1023 KB`() {
        // The carry is for values that round across the boundary, not a licence
        // to overstate: this one is 1023 KB, and calling it 1.0 MB would be a
        // bigger lie than the one the rule is about.
        assertEquals("1023 KB/s", formatRate(1_047_552))
    }

    @Test
    fun `each unit is used while it is the readable one`() {
        assertEquals("0 B/s", formatRate(0))
        assertEquals("512 B/s", formatRate(512))
        assertEquals("1 KB/s", formatRate(1_024))
        assertEquals("1.0 MB/s", formatRate(1_048_576))
        assertEquals("1.5 MB/s", formatRate(1_572_864))
        assertEquals("1.0 GB/s", formatRate(1_073_741_824))
        // Beyond the largest unit the number just keeps growing.
        assertEquals("2048.0 GB/s", formatRate(2_199_023_255_552))
    }

    @Test
    fun `zero is a number, never a dash`() {
        assertEquals("0 B/s", formatRate(0))
        assertEquals("0 B", formatBytes(0))
    }

    @Test
    fun `totals use the same scale and one decimal from megabytes up`() {
        assertEquals("999 B", formatBytes(999))
        assertEquals("1 KB", formatBytes(1_024))
        assertEquals("1.0 MB", formatBytes(1_048_576))
        // One decimal, which is what the design's own example shows ("1.2 GB");
        // the old code used two here and would have written "1.20 GB".
        assertEquals("2.5 GB", formatBytes(2_684_354_560))
    }
}
