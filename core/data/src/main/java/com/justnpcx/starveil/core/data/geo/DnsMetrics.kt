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


/**
 * One lookup, as the per-query log shows it.
 *
 * The hostname is here because the question "which name was slow" cannot be
 * answered by a percentile, and this ring never leaves the device. The resolver
 * is the DoH endpoint's address (or the system resolver's label), so a fallback
 * is visible as a fallback rather than as a slow success.
 */
data class DnsEntry(
    val host: String,
    val resolver: String,
    val durationMs: Long,
    val outcome: DnsOutcome,
    val atMs: Long,
)

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

    // The per-query log: a second, smaller ring. It holds what a percentile
    // cannot — which name was slow — and stops at 100 entries so a proxy client
    // resolving for hours cannot grow it.
    private val entries = arrayOfNulls<DnsEntry>(ENTRY_CAPACITY)
    private var entryCount = 0
    private var entryCursor = 0

    /** Sampled by the switch; checked on every record. */
    @Volatile
    var enabled: Boolean = true

    @Synchronized
    fun record(
        durationMs: Long,
        outcome: DnsOutcome,
        /** The name that was looked up; empty for a literal (nothing was). */
        host: String = "",
        /** The endpoint that answered, or "system" for a fallback. */
        resolver: String = "",
        atMs: Long = System.currentTimeMillis(),
    ) {
        if (!enabled) return
        durations[cursor] = durationMs.coerceAtLeast(0L)
        outcomes[cursor] = outcome.ordinal
        cursor = (cursor + 1) % CAPACITY
        if (count < CAPACITY) count++

        entries[entryCursor] = DnsEntry(
            host = host,
            resolver = resolver,
            durationMs = durationMs.coerceAtLeast(0L),
            outcome = outcome,
            atMs = atMs,
        )
        entryCursor = (entryCursor + 1) % ENTRY_CAPACITY
        if (entryCount < ENTRY_CAPACITY) entryCount++
    }

    /**
     * The per-query ring, newest first.
     *
     * A copy rather than the live array: the writer is on the DNS path and must
     * never wait for a screen to finish reading.
     */
    @Synchronized
    fun recentEntries(): List<DnsEntry> {
        if (entryCount == 0) return emptyList()
        val newestFirst = ArrayList<DnsEntry>(entryCount)
        var index = (entryCursor - 1 + ENTRY_CAPACITY) % ENTRY_CAPACITY
        repeat(entryCount) {
            entries[index]?.let { newestFirst.add(it) }
            index = (index - 1 + ENTRY_CAPACITY) % ENTRY_CAPACITY
        }
        return newestFirst
    }

    /**
     * The log as text, for the copy button.
     *
     * Domain, outcome and duration only: the resolver's address is a server the
     * user chose, and the export is meant to be pasteable into a bug report
     * without carrying it. Node addresses, tokens and subscription links are
     * not in this ring in the first place.
     */
    @Synchronized
    fun exportText(): String {
        val now = snapshot()
        val header = "Starveil DNS samples (GeoIP probe, not tunnel DNS)\n" +
            "samples=%d p50=%dms p95=%dms fallback=%d%% cache=%d%%".format(
                now.samples,
                now.p50Ms,
                now.p95Ms,
                now.fallbackPct,
                now.cacheHitPct,
            )
        val body = recentEntries().asReversed().joinToString("\n") { entry ->
            val stamp = java.time.Instant.ofEpochMilli(entry.atMs)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
            "%s %s %dms %s".format(stamp, entry.host.ifBlank { "(literal)" }, entry.durationMs, entry.outcome.name.lowercase())
        }
        return if (body.isEmpty()) header else header + "\n" + body
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
        entryCount = 0
        entryCursor = 0
        entries.fill(null)
    }

    private companion object {
        const val CAPACITY = 512

        /** 100 rows: the per-query log's fixed size, by requirement. */
        const val ENTRY_CAPACITY = 100

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
