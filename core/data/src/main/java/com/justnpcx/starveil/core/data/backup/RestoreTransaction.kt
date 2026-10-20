package com.justnpcx.starveil.core.data.backup

import androidx.room.withTransaction
import com.justnpcx.starveil.core.data.StarveilDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a restore's writes as one unit.
 *
 * An interface rather than the database itself: the feature layer must not see
 * Room, and the manager's tests must not need an emulator. The production
 * implementation is Room's own transaction, so a failure anywhere in the block
 * leaves the database exactly as it was.
 */
interface RestoreTransaction {
    suspend fun <T> run(block: suspend () -> T): T
}

@Singleton
class RoomRestoreTransaction @Inject constructor(
    private val database: StarveilDatabase,
) : RestoreTransaction {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction { block() }
}
