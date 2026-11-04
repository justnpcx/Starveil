package com.justnpcx.starveil.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.justnpcx.starveil.core.data.entity.RuleHitEntity
import kotlinx.coroutines.flow.Flow

/**
 * The hit counters.
 *
 * Writes are UPSERT-with-add rather than read-modify-write: the recorder
 * batches in memory, but two flushes can still overlap (a window tick and a
 * buffer that filled), and `count = count + :delta` is the only form that
 * cannot lose one of them.
 */
@Dao
abstract class RuleHitDao {

    @Query("SELECT * FROM rule_hits ORDER BY count DESC, ruleText ASC")
    abstract fun observeAll(): Flow<List<RuleHitEntity>>

    @Query("SELECT * FROM rule_hits ORDER BY count DESC, ruleText ASC")
    abstract suspend fun getAll(): List<RuleHitEntity>

    @Query(
        """
        INSERT INTO rule_hits(ruleText, outbound, count, lastHitAt)
        VALUES(:ruleText, :outbound, :delta, :atMs)
        ON CONFLICT(ruleText, outbound)
        DO UPDATE SET count = count + :delta, lastHitAt = :atMs
        """,
    )
    abstract suspend fun add(ruleText: String, outbound: String, delta: Long, atMs: Long)

    /** The window start, or null before anything has been counted. */
    @Query("SELECT startedAt FROM rule_hit_window WHERE id = 0")
    abstract suspend fun windowStart(): Long?

    @Query("SELECT startedAt FROM rule_hit_window WHERE id = 0")
    abstract fun observeWindowStart(): Flow<Long?>

    @Query("INSERT OR REPLACE INTO rule_hit_window(id, startedAt) VALUES(0, :atMs)")
    abstract suspend fun setWindowStart(atMs: Long)

    @Query("DELETE FROM rule_hits")
    abstract suspend fun clearHits()

    /**
     * Clears the counters and opens a new window, in one transaction.
     *
     * Split apart, a crash between the two would leave new counts under the
     * old window label — the page would say "since Monday" about numbers that
     * started on Tuesday.
     */
    @Transaction
    open suspend fun reset(atMs: Long) {
        clearHits()
        setWindowStart(atMs)
    }

    /** Opens the window on first use without touching counts. */
    @Transaction
    open suspend fun ensureWindow(atMs: Long) {
        if (windowStart() == null) setWindowStart(atMs)
    }
}
