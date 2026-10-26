package com.justnpcx.starveil.core.data.geo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ring behind the developer options row, and the number it exists for.
 *
 * §7.1 asks for a p50 under 30 ms. A percentile is only worth reporting if the
 * samples it came from are the ones that happened, so the tests pin the rank
 * rule, the wrap-around, and that the switch really stops the recording.
 */
class DnsMetricsTest {

    @Test
    fun `an empty ring reports zero rather than a made-up percentile`() {
        val stats = DnsMetrics().snapshot()

        assertEquals(0, stats.samples)
        assertEquals(0L, stats.p50Ms)
        assertEquals(0L, stats.p95Ms)
    }

    @Test
    fun `percentiles are nearest-rank, not interpolated`() {
        val metrics = DnsMetrics()
        // 1..100 ms: p50 is the 50th value, p95 the 95th.
        (1L..100L).forEach { metrics.record(it, DnsOutcome.DOH) }

        val stats = metrics.snapshot()

        assertEquals(100, stats.samples)
        assertEquals(50L, stats.p50Ms)
        assertEquals(95L, stats.p95Ms)
    }

    @Test
    fun `a p95 of five samples is the fifth`() {
        val metrics = DnsMetrics()
        listOf(10L, 20L, 30L, 40L, 90L).forEach { metrics.record(it, DnsOutcome.DOH) }

        assertEquals(90L, metrics.snapshot().p95Ms)
    }

    @Test
    fun `the ring keeps the newest samples and forgets the oldest`() {
        val metrics = DnsMetrics()
        repeat(600) { metrics.record(it.toLong(), DnsOutcome.DOH) }

        val stats = metrics.snapshot()

        // 512 kept, and they are the last 512: 88..599.
        assertEquals(512, stats.samples)
        assertEquals(343L, stats.p50Ms)
        assertEquals(574L, stats.p95Ms)
    }

    @Test
    fun `cache hits and fallbacks are counted as shares`() {
        val metrics = DnsMetrics()
        repeat(4) { metrics.record(5L, DnsOutcome.CACHE) }
        metrics.record(30L, DnsOutcome.FALLBACK)
        repeat(5) { metrics.record(10L, DnsOutcome.DOH) }

        val stats = metrics.snapshot()

        assertEquals(10, stats.samples)
        assertEquals(40, stats.cacheHitPct)
        assertEquals(10, stats.fallbackPct)
    }

    @Test
    fun `the per-query log keeps the newest hundred`() {
        val metrics = DnsMetrics()
        repeat(150) { i ->
            metrics.record(10L, DnsOutcome.DOH, host = "n$i.example.com", resolver = "223.5.5.5")
        }

        val entries = metrics.recentEntries()

        assertEquals(100, entries.size)
        // Newest first, and the first 50 were evicted.
        assertEquals("n149.example.com", entries.first().host)
        assertEquals("n50.example.com", entries.last().host)
    }

    @Test
    fun `the per-query log is off with the switch, like the aggregate`() {
        val metrics = DnsMetrics()
        metrics.enabled = false
        metrics.record(10L, DnsOutcome.DOH, host = "a.example.com")

        assertEquals(0, metrics.recentEntries().size)
    }

    @Test
    fun `the export carries domains and timings, never the resolver`() {
        val metrics = DnsMetrics()
        metrics.record(12L, DnsOutcome.DOH, host = "node.example.com", resolver = "223.5.5.5")
        metrics.record(40L, DnsOutcome.FALLBACK, host = "other.example.com", resolver = "system")

        val text = metrics.exportText()

        assertEquals(true, text.contains("node.example.com"))
        assertEquals(true, text.contains("12ms"))
        assertEquals(false, text.contains("223.5.5.5"))
        assertEquals(false, text.contains("system"))
    }

    @Test
    fun `clearing empties the per-query log too`() {
        val metrics = DnsMetrics()
        metrics.record(10L, DnsOutcome.DOH, host = "a.example.com")
        metrics.clear()

        assertEquals(0, metrics.recentEntries().size)
        assertEquals(0, metrics.snapshot().samples)
    }

    @Test
    fun `the switch stops recording without losing what is already there`() {
        val metrics = DnsMetrics()
        metrics.record(10L, DnsOutcome.DOH)
        metrics.enabled = false
        metrics.record(999L, DnsOutcome.DOH)

        assertEquals(1, metrics.snapshot().samples)
        metrics.enabled = true
        assertEquals(10L, metrics.snapshot().p50Ms)
    }
}
