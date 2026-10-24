package com.justnpcx.starveil.core.data.geo

import javax.inject.Inject
import javax.inject.Singleton

/** Where a DNS answer came from. */
enum class DnsOutcome {
    /** Answered by a DoH endpoint. */
    DOH,

    /** Every DoH endpoint failed and the system resolver answered instead. */
    FALLBACK,

    /** Answered from the process's own memo; no query left the device. */
    CACHE,

    /** The host was already an address; nothing to resolve. */
    LITERAL,

    /** Every DoH endpoint failed and there was no fallback. */
    FAILED,
}

/** What the ring holds right now, as the developer options row shows it. */
data class DnsSnapshot(
    val samples: Int,
    val p50Ms: Long,
    val p95Ms: Long,
    val cacheHitPct: Int,
    val fallbackPct: Int,
) {
    companion object {
        val EMPTY = DnsSnapshot(samples = 0, p50Ms = 0, p95Ms = 0, cacheHitPct = 0, fallbackPct = 0)
    }
}

/**
 * Timing for every DNS answer the app gets, kept in a fixed ring.
 *
 * The number this exists for is p50 under 30 ms (§7.1). It is a ring rather
 * than a counter because a percentile needs the samples, and it is fixed rather
 * than growing because a proxy client can resolve for hours: 512 entries is a
 * few days of ordinary use and 4 KB of memory.
 *
 * Off by the switch in developer options, and a no-op when off — the point of
 * the switch is to be able to stop paying for the measurement, not to hide it.
 */
@Singleton
class DnsMetrics @Inject constructor() {

    private val durations = LongArray(CAPACITY)
    private val outcomes = IntArray(CAPACITY)
    private var count = 0
    private var cursor = 0

    /** Sampled by the switch; checked on every record. */
    @Volatile
    var enabled: Boolean = true

    @Synchronized
    fun record(durationMs: Long, outcome: DnsOutcome) {
        if (!enabled) return
        durations[cursor] = durationMs.coerceAtLeast(0L)
        outcomes[cursor] = outcome.ordinal
        cursor = (cursor + 1) % CAPACITY
        if (count < CAPACITY) count++
    }

    @Synchronized
    fun snapshot(): DnsSnapshot {
        if (count == 0) return DnsSnapshot.EMPTY
        val sorted = LongArray(count) { durations[it] }.apply { sort() }
        var cache = 0
        var fallback = 0
        for (i in 0 until count) {
            when (DnsOutcome.entries[outcomes[i]]) {
                DnsOutcome.CACHE -> cache++
                DnsOutcome.FALLBACK -> fallback++
                else -> Unit
            }
        }
        return DnsSnapshot(
            samples = count,
            p50Ms = percentile(sorted, 50),
            p95Ms = percentile(sorted, 95),
            cacheHitPct = cache * 100 / count,
            fallbackPct = fallback * 100 / count,
        )
    }

    @Synchronized
    fun clear() {
        count = 0
        cursor = 0
    }

    private companion object {
        const val CAPACITY = 512

        /**
         * Nearest-rank: the smallest value at or above the requested share.
         *
         * Not interpolated on purpose. A p95 of "27.4 ms" implies a precision
         * the sample cannot support; the honest answer is one of the values
         * that was actually measured.
         */
        fun percentile(sorted: LongArray, pct: Int): Long {
            if (sorted.isEmpty()) return 0
            val rank = ((pct / 100.0) * sorted.size).let { Math.ceil(it).toInt() }
            return sorted[(rank - 1).coerceIn(0, sorted.size - 1)]
        }
    }
}
