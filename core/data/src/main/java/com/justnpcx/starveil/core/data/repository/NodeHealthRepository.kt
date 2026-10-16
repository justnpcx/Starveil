package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.LatencySampleDao
import com.justnpcx.starveil.core.data.entity.LatencySampleEntity
import com.justnpcx.starveil.core.model.HEALTH_WINDOW_MS
import com.justnpcx.starveil.core.model.LatencySample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The latency history behind the node health curve (§7.2).
 *
 * Feature modules depend on this interface, never on [LatencySampleDao] — the
 * data-layer boundary.
 */
interface NodeHealthRepository {
    /**
     * The current window's samples, grouped by node and oldest first within
     * each node.
     *
     * Grouped here rather than in the UI because the node id is a storage
     * detail — the domain sample is only (when, how long). One query for the
     * whole list rather than one per node: a subscription can hold hundreds of
     * nodes, and subscribing per node would mean hundreds of live queries for
     * one screen.
     *
     * The window is applied at read time as well as at write time. The store is
     * already pruned, so this is bounded, but reading it fresh is what lets the
     * boundary *slide* with the clock instead of freezing at whatever `now` was
     * when the query was built.
     */
    fun observeAll(): Flow<Map<Long, List<LatencySample>>>

    /**
     * Record one probe and prune the store.
     *
     * [latencyMs] below zero is a probe that did not come back; it is stored so
     * the loss is visible rather than skipped.
     */
    suspend fun record(profileId: Long, latencyMs: Long, atMs: Long)

    /** Drop a node's history — for when the node itself is gone. */
    suspend fun forget(profileId: Long)
}

class RoomNodeHealthRepository @Inject constructor(
    private val dao: LatencySampleDao,
) : NodeHealthRepository {

    override fun observeAll(): Flow<Map<Long, List<LatencySample>>> =
        dao.observeAll().map { rows -> windowSamples(rows, nowMs = System.currentTimeMillis()) }

    override suspend fun record(profileId: Long, latencyMs: Long, atMs: Long) {
        dao.insert(
            LatencySampleEntity(profileId = profileId, atMs = atMs, latencyMs = latencyMs),
        )
        // Prune on write rather than on a timer: the store then cannot grow
        // while nothing is sampling it, and there is no scheduler to get wrong.
        dao.deleteOlderThan(atMs - HEALTH_WINDOW_MS)
        dao.trimToCap(profileId, MAX_SAMPLES_PER_NODE)
    }

    override suspend fun forget(profileId: Long) = dao.deleteForProfile(profileId)

    private companion object {
        /**
         * Twice the window's worth at the 30 s cadence. The cap is a backstop
         * for a backwards clock jump, which age-pruning alone cannot catch.
         */
        const val MAX_SAMPLES_PER_NODE = 120
    }
}

internal fun LatencySampleEntity.toDomain(): LatencySample =
    LatencySample(atMs = atMs, latencyMs = latencyMs)

/**
 * The window's samples, grouped by node, oldest first within each node.
 *
 * Pure, and the clock is a parameter, so the boundary can be pinned exactly —
 * including that a sample *at* the edge is inside. [observeAll] reads the clock
 * once per emission, which is what makes the boundary slide rather than freeze
 * at subscription time.
 */
internal fun windowSamples(
    rows: List<LatencySampleEntity>,
    nowMs: Long,
): Map<Long, List<LatencySample>> {
    val cutoff = nowMs - HEALTH_WINDOW_MS
    return rows.asSequence()
        .filter { it.atMs >= cutoff }
        .groupBy({ it.profileId }, { it.toDomain() })
        // Sorted here rather than relying on the query's ORDER BY: the chart's
        // x-axis and the loss dots are both positional, so "oldest first" is
        // part of this function's contract, not of the caller's.
        .mapValues { (_, samples) -> samples.sortedBy { it.atMs } }
}
