package com.justnpcx.starveil.core.model

import kotlin.math.roundToInt

/**
 * Picking a node to connect to, as a pure function.
 *
 * The task is not "which node is fastest" — the kernel's urltest group already
 * answers that, and answers it with live probes. This is for the case where
 * there is no group: a flat list of nodes and a user who does not want to pick
 * one by hand. So the ranking has to be explainable in one sentence, because
 * the whole point is that the user can see *why* and disagree.
 *
 * Nothing here reads a clock or a database. [nowMs] is passed in, so the rules
 * are unit-testable without a device — which is the only kind of verification
 * this machine can do.
 */

/** Consecutive failures that put a node in time-out. */
const val FAILURE_THRESHOLD = 3

/** How long a node stays out after [FAILURE_THRESHOLD] failures in a row. */
const val COOLDOWN_MS = 30 * 60 * 1000L

/** At or below this, latency earns full marks. */
const val LATENCY_FLOOR_MS = 50L

/** At or above this, latency earns none. */
const val LATENCY_CEILING_MS = 400L

/**
 * A 0–10099 health score, higher is better.
 *
 * Availability is the high-order part and latency the low-order, so a node that
 * answers more often always wins, and latency only orders nodes that are
 * equally reliable. That ordering is deliberate: a fast node that drops one
 * connection in ten is worse than a slower one that never does, and a weighted
 * average would let the fast one win.
 *
 * An untested node scores 0. It is not "unknown but promising" — nothing has
 * been measured, so it must not outrank a node that has.
 */
fun healthScore(health: NodeHealth?): Int {
    if (health == null || health.samples.isEmpty()) return 0
    val availability = health.availabilityPct.coerceIn(0f, 100f).roundToInt()
    val latency = health.emaMs ?: return availability * 100
    val clamped = latency.coerceIn(LATENCY_FLOOR_MS, LATENCY_CEILING_MS)
    // 0–99, never 100: a full 100 would carry into the availability digit and
    // let a faster node outrank a more reliable one.
    val latencyPoints = (99L * (LATENCY_CEILING_MS - clamped) /
        (LATENCY_CEILING_MS - LATENCY_FLOOR_MS)).toInt()
    return availability * 100 + latencyPoints
}

/** How many probes failed in a row, counting back from the newest. */
fun trailingFailures(health: NodeHealth?): Int =
    health?.samples?.takeLastWhile { !it.reachable }?.size ?: 0

/**
 * Milliseconds left in this node's time-out; 0 when it is usable.
 *
 * The clock starts at the *last* failure, so a node that keeps failing stays
 * out rather than becoming eligible again mid-outage.
 */
fun cooldownRemainingMs(health: NodeHealth?, nowMs: Long): Long {
    if (health == null) return 0
    val failures = health.samples.takeLastWhile { !it.reachable }
    if (failures.size < FAILURE_THRESHOLD) return 0
    val since = nowMs - failures.last().atMs
    return (COOLDOWN_MS - since).coerceAtLeast(0)
}

/** One node, as the picker sees it. */
data class NodeCandidate(
    val profileId: Long,
    val region: NodeRegion?,
    val health: NodeHealth?,
)

/** Why this node was picked. Rendered by the UI, never by the picker. */
sealed interface RecommendationReason {
    /** It scored highest and nothing else came close. */
    data class Healthiest(val score: Int) : RecommendationReason

    /**
     * Several nodes scored the same; [region] is the one the user prefers, or
     * null when there was no preference to apply and the lowest id won.
     */
    data class TiedOnScore(val score: Int, val region: NodeRegion?) : RecommendationReason

    /** Everything is in time-out; this is the one that recovers soonest. */
    data class AllCoolingDown(val minutesLeft: Long) : RecommendationReason

    /** Nothing has been measured yet, so the pick is arbitrary but stable. */
    data object NothingMeasured : RecommendationReason
}

data class NodeRecommendation(
    val profileId: Long,
    val score: Int,
    val reason: RecommendationReason,
)

object NodePicker {

    /**
     * The node to use, or null when there is nothing to choose from.
     *
     * [preferredRegion] only ever breaks an exact tie. It is a preference, not
     * a filter: a much healthier node in another region still wins, because
     * "I like Hong Kong" is not worth a connection that keeps dropping.
     */
    fun recommend(
        candidates: List<NodeCandidate>,
        preferredRegion: NodeRegion? = null,
        nowMs: Long,
    ): NodeRecommendation? {
        if (candidates.isEmpty()) return null

        val usable = candidates.filter { cooldownRemainingMs(it.health, nowMs) == 0L }
        if (usable.isEmpty()) return soonestToRecover(candidates, nowMs)

        val scored = usable.map { it to healthScore(it.health) }
        val best = scored.maxOf { it.second }
        val tied = scored.filter { it.second == best }

        if (tied.size == 1) {
            return NodeRecommendation(
                profileId = tied.single().first.profileId,
                score = best,
                reason = if (best == 0) {
                    RecommendationReason.NothingMeasured
                } else {
                    RecommendationReason.Healthiest(best)
                },
            )
        }

        val preferred = preferredRegion?.let { region ->
            tied.firstOrNull { it.first.region == region }
        }
        val winner = preferred ?: tied.minBy { it.first.profileId }
        return NodeRecommendation(
            profileId = winner.first.profileId,
            score = best,
            reason = RecommendationReason.TiedOnScore(best, preferred?.first?.region),
        )
    }

    private fun soonestToRecover(
        candidates: List<NodeCandidate>,
        nowMs: Long,
    ): NodeRecommendation? {
        val soonest = candidates.minByOrNull { cooldownRemainingMs(it.health, nowMs) }
            ?: return null
        val remaining = cooldownRemainingMs(soonest.health, nowMs)
        return NodeRecommendation(
            profileId = soonest.profileId,
            score = healthScore(soonest.health),
            reason = RecommendationReason.AllCoolingDown(
                // Rounded up: "0 minutes left" while still in time-out reads
                // like a bug.
                minutesLeft = (remaining + 59_999) / 60_000,
            ),
        )
    }
}
