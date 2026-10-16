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
     * Every node's samples at or after [sinceMs], oldest first, across all
     * nodes in one query. Callers group by node.
     */
    fun observeSince(sinceMs: Long): Flow<List<LatencySample>>

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

    override fun observeSince(sinceMs: Long): Flow<List<LatencySample>> =
        dao.observeSince(sinceMs).map { rows -> rows.map { it.toDomain() } }

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
