package com.justnpcx.starveil.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.justnpcx.starveil.core.data.entity.LatencySampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LatencySampleDao {
    /**
     * Every stored sample, oldest first.
     *
     * Not filtered by a cutoff here on purpose. The store is already pruned to
     * the window on write, so this is not an unbounded read; applying the
     * window again on the reading side is what lets its boundary *slide* with
     * the clock instead of freezing at whatever `now` was when the query was
     * built.
     *
     * One query for the whole list rather than one per node: a subscription can
     * hold hundreds of nodes, and subscribing per node would mean hundreds of
     * live queries for one screen.
     */
    @Query("SELECT * FROM latency_samples ORDER BY atMs ASC")
    fun observeAll(): Flow<List<LatencySampleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LatencySampleEntity)

    /** Drop samples older than [cutoffMs] — the window, applied on write. */
    @Query("DELETE FROM latency_samples WHERE atMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM latency_samples WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    /**
     * Keep only the newest [keep] samples for [profileId].
     *
     * A safety net, not the primary bound: age-pruning already caps a node at
     * one window's worth of samples. This covers the case age-pruning cannot —
     * a clock that jumps backwards makes the cutoff land in the past, so
     * nothing ages out and the table grows without limit. Bounding by count is
     * immune to that.
     */
    @Query(
        """
        DELETE FROM latency_samples
        WHERE profileId = :profileId
          AND atMs < (
            SELECT atMs FROM latency_samples
            WHERE profileId = :profileId
            ORDER BY atMs DESC
            LIMIT 1 OFFSET :keep
          )
        """,
    )
    suspend fun trimToCap(profileId: Long, keep: Int)
}
