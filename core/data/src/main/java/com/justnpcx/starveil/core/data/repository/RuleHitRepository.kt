package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.RuleHitDao
import com.justnpcx.starveil.core.model.RuleHit
import com.justnpcx.starveil.core.model.RuleHitDelta
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The rule hit counters, in domain terms.
 *
 * Feature modules depend on this, never on the DAO: the counters are a
 * diagnostic, and nothing in the UI should be able to write a count that the
 * kernel did not report.
 */
interface RuleHitRepository {
    /** Every counted decision, busiest first. */
    fun observeAll(): Flow<List<RuleHit>>

    /** When the current window opened; null until the first flush. */
    fun observeWindowStart(): Flow<Long?>

    /** Adds [deltas] to the counters in one transaction. */
    suspend fun record(deltas: List<RuleHitDelta>, atMs: Long)

    /** Clears every count and opens a new window at [atMs]. */
    suspend fun reset(atMs: Long)

    /** Stamps the window if this is the first count after install or reset. */
    suspend fun ensureWindow(atMs: Long)
}

class RoomRuleHitRepository @Inject constructor(
    private val dao: RuleHitDao,
) : RuleHitRepository {

    override fun observeAll(): Flow<List<RuleHit>> =
        dao.observeAll().map { rows ->
            rows.map { RuleHit(it.ruleText, it.outbound, it.count, it.lastHitAt) }
        }

    override fun observeWindowStart(): Flow<Long?> = dao.observeWindowStart()

    override suspend fun record(deltas: List<RuleHitDelta>, atMs: Long) {
        // The window opens with the first count it contains, so "since" is
        // never later than the numbers under it.
        dao.ensureWindow(atMs)
        for (delta in deltas) {
            if (delta.count <= 0L) continue
            dao.add(delta.ruleText, delta.outbound, delta.count, atMs)
        }
    }

    override suspend fun reset(atMs: Long) = dao.reset(atMs)

    override suspend fun ensureWindow(atMs: Long) = dao.ensureWindow(atMs)
}
