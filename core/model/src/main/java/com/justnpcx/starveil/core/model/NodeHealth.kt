package com.justnpcx.starveil.core.model

import kotlin.math.roundToLong

/**
 * One latency probe of a node.
 *
 * A negative [latencyMs] is a probe that did not come back — a timeout or an
 * unreachable host. That is a *sample* rather than an absence on purpose: a
 * node that stops answering is the thing a health curve exists to show, and
 * dropping those samples would make an unreliable node look perfect. The
 * design system draws exactly these as the red loss dots (§7.2).
 */
data class LatencySample(
    val atMs: Long,
    val latencyMs: Long,
) {
    /** Whether the probe came back inside the window. */
    val reachable: Boolean get() = latencyMs >= 0
}

/**
 * How much of the window the node answered in.
 *
 * The bands are the design system's (§7.2): above 99% green, 95–99% yellow,
 * below 95% red. The boundaries are strict at the top on purpose — a node at
 * exactly 99% is not "above 99%", and rounding it up would be the one
 * direction that flatters a node rather than warning about it.
 */
enum class AvailabilityBand { GOOD, FAIR, POOR }

/**
 * The colour of the node card's status dot (§6.3): green, grey, red.
 *
 * Kept to the three the design names. "The last probe failed" and "answering
 * but mostly lossy" are both red — the card's latency text already tells them
 * apart ("timeout" versus a number), so a fourth colour would only be a
 * fourth thing to keep consistent.
 */
enum class NodeStatus { UP, UNTESTED, DOWN }

/**
 * A node's health over the sampling window, derived entirely from its samples.
 *
 * This is the "health score" the architecture audit asked for, and it is a
 * pure function of its input: nothing here reads a clock or a database, so it
 * is unit-testable without a device. The caller supplies the samples already
 * narrowed to the window.
 */
data class NodeHealth(
    /** The window's samples, oldest first. */
    val samples: List<LatencySample>,
    /** Smoothed latency of the successful probes, or null when none succeeded. */
    val emaMs: Long?,
    /** Share of the window the node answered in, 0–100. */
    val availabilityPct: Float,
    val band: AvailabilityBand,
    /**
     * Fewer than [MIN_SAMPLES] samples: too few to draw a curve from. The
     * chart says so rather than drawing a misleading line (§7.2).
     */
    val collecting: Boolean,
) {
    /** The most recent probe, or null when the window is empty. */
    val latest: LatencySample? get() = samples.lastOrNull()

    /** The status dot's colour, from the latest probe and the window's band. */
    val status: NodeStatus
        get() = when {
            samples.isEmpty() -> NodeStatus.UNTESTED
            latest?.reachable != true -> NodeStatus.DOWN
            band == AvailabilityBand.POOR -> NodeStatus.DOWN
            else -> NodeStatus.UP
        }

    companion object {
        /** Below this many samples a curve is noise, not a shape (§7.2). */
        const val MIN_SAMPLES = 5
    }
}

/**
 * EMA smoothing factor.
 *
 * ~5 samples, which at the 30 s sampling cadence is roughly a 2.5-minute
 * memory: long enough that one slow handshake does not spike the curve, short
 * enough that a node going bad is visible within a minute or so rather than
 * being averaged away.
 */
const val EMA_ALPHA = 0.2

/**
 * Share of [samples] that came back, 0–100. An empty window is 0.
 */
fun availabilityPct(samples: List<LatencySample>): Float {
    if (samples.isEmpty()) return 0f
    val reachable = samples.count { it.reachable }
    return reachable * 100f / samples.size
}

/** The band for an availability percentage (§7.2). */
fun bandOf(pct: Float): AvailabilityBand = when {
    pct > 99f -> AvailabilityBand.GOOD
    pct >= 95f -> AvailabilityBand.FAIR
    else -> AvailabilityBand.POOR
}

/**
 * Exponential moving average of the successful probes, or null when none
 * succeeded.
 *
 * Failures are excluded rather than fed in as zero. A timeout is not a latency
 * of zero, and treating it as one would drag the curve *down* for a node that
 * is unreachable — reading as "fast" at the exact moment the node is worst.
 * Loss is expressed by the red dots and the availability bar, not by bending
 * the latency line.
 */
fun emaMs(samples: List<LatencySample>, alpha: Double = EMA_ALPHA): Long? {
    var ema: Double? = null
    for (sample in samples) {
        if (!sample.reachable) continue
        val value = sample.latencyMs.toDouble()
        // Seed on the first success rather than on an assumed baseline: there
        // is no honest prior for a node we have never measured.
        ema = ema?.let { alpha * value + (1 - alpha) * it } ?: value
    }
    return ema?.roundToLong()
}

/**
 * Derive a node's health from its samples.
 *
 * The samples are sorted by time first: callers pass whatever order their
 * query returned, and both the EMA and "the latest probe" are order-dependent.
 */
fun nodeHealthOf(samples: List<LatencySample>): NodeHealth {
    val sorted = samples.sortedBy { it.atMs }
    val availability = availabilityPct(sorted)
    return NodeHealth(
        samples = sorted,
        emaMs = emaMs(sorted),
        availabilityPct = availability,
        band = bandOf(availability),
        collecting = sorted.size < NodeHealth.MIN_SAMPLES,
    )
}
