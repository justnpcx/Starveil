package com.justnpcx.starveil.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.justnpcx.starveil.core.data.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM `groups` ORDER BY id ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?

    /**
     * The group a subscription URL already has, if any.
     *
     * One URL is one group that refreshes; without this lookup every import
     * inserted a new group and the same subscription's nodes ended up split
     * across duplicates.
     */
    @Query("SELECT * FROM `groups` WHERE subscriptionUrl = :url LIMIT 1")
    suspend fun findBySubscriptionUrl(url: String): GroupEntity?

    /**
     * The group that already represents [region], if any.
     *
     * Keyed on the region rather than the name so re-running the grouping
     * updates the existing group even after someone renamed it — the name is
     * a label, the region is the identity.
     */
    @Query("SELECT * FROM `groups` WHERE region = :region LIMIT 1")
    suspend fun findByRegion(region: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupEntity): Long

    @Update
    suspend fun update(entity: GroupEntity)

    @Delete
    suspend fun delete(entity: GroupEntity)

    @Query("DELETE FROM `groups` WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Delete a group and, when [deleteProfiles], the nodes it owns — atomically.
     *
     * Two statements in one transaction matters here: an interruption between
     * them would leave a group with no nodes and nothing left to refresh it,
     * which is indistinguishable from an empty subscription. A region group
     * owns no profiles (its members are derived), so callers pass false.
     */
    @Transaction
    suspend fun deleteWithProfiles(groupId: Long, deleteProfiles: Boolean) {
        if (deleteProfiles) deleteProfilesOfGroup(groupId)
        deleteById(groupId)
    }

    @Query("DELETE FROM profiles WHERE groupId = :groupId")
    suspend fun deleteProfilesOfGroup(groupId: Long)
}
