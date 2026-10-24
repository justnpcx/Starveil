package com.justnpcx.starveil.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Insert one sample and cap the node's history.
     *
     * No age pruning. There used to be, and it was the reason the health card
     * had nothing to draw on the first frame: the sampler records as soon as
     * the app starts, the cutoff is thirty minutes, and an app opened the next
     * morning deleted its entire history in the same transaction that added
     * the first new sample. The card then said "collecting" until five probes
     * had landed.
     *
     * The cap alone bounds the table (120 rows per node, twice the window at
     * the 30 s cadence), so nothing grows without limit. What the *window*
     * means is a reading concern: windowSamples() decides what is current, and
     * the repository falls back to the newest stored rows when the window is
     * thin, so an old curve is drawn rather than a blank one.
     */
    @Transaction
    suspend fun recordSample(entity: LatencySampleEntity, keep: Int) {
        insert(entity)
        trimToCap(entity.profileId, keep)
    }
}
