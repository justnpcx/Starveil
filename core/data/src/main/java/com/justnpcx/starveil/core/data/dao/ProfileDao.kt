package com.justnpcx.starveil.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.justnpcx.starveil.core.data.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE groupId = :groupId ORDER BY id ASC")
    fun observeByGroup(groupId: Long): Flow<List<ProfileEntity>>

    @Query("SELECT id FROM profiles WHERE groupId = :groupId")
    suspend fun getProfileIdsByGroup(groupId: Long): List<Long>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProfileEntity): Long

    @Update
    suspend fun update(entity: ProfileEntity)

    @Delete
    suspend fun delete(entity: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM profiles WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: Long)

    /** Delete-then-insert in one transaction, so a refresh is all-or-nothing. */
    @Transaction
    suspend fun replaceGroup(groupId: Long, entities: List<ProfileEntity>) {
        deleteByGroup(groupId)
        for (entity in entities) upsert(entity)
    }
}
