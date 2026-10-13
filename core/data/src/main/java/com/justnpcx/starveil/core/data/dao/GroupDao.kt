package com.justnpcx.starveil.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.justnpcx.starveil.core.data.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM `groups` ORDER BY id ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupEntity): Long

    @Update
    suspend fun update(entity: GroupEntity)

    @Delete
    suspend fun delete(entity: GroupEntity)

    @Query("DELETE FROM `groups` WHERE id = :id")
    suspend fun deleteById(id: Long)
}
