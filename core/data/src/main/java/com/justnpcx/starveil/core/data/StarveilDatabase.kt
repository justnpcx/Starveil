package com.justnpcx.starveil.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.justnpcx.starveil.core.data.dao.GroupDao
import com.justnpcx.starveil.core.data.dao.LatencySampleDao
import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.dao.RuleDao
import com.justnpcx.starveil.core.data.dao.RuleHitDao
import com.justnpcx.starveil.core.data.entity.GroupEntity
import com.justnpcx.starveil.core.data.entity.LatencySampleEntity
import com.justnpcx.starveil.core.data.entity.ProfileEntity
import com.justnpcx.starveil.core.data.entity.RuleEntity
import com.justnpcx.starveil.core.data.entity.RuleHitEntity
import com.justnpcx.starveil.core.data.entity.RuleHitWindowEntity

/**
 * The app's only Room database.
 *
 * MIGRATION POLICY — read before changing the schema:
 * - NEVER add `fallbackToDestructiveMigration()`. A failed migration must
 *   surface as an explicit error, never silently wipe the user's nodes,
 *   groups and rules (the Mirage-era data-loss failure mode).
 * - Every schema change ships with an explicit `Migration` (or
 *   `AutoMigration`) plus a `MigrationTestHelper` test.
 * - Recovery path for a user stuck on a failed migration is backup
 *   restore, not destructive fallback.
 * - Schemas are exported to `core/data/schemas/` (see build.gradle.kts)
 *   so every schema change is reviewable in git.
 */
@Database(
    entities = [
        ProfileEntity::class,
        GroupEntity::class,
        RuleEntity::class,
        LatencySampleEntity::class,
        RuleHitEntity::class,
        RuleHitWindowEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class StarveilDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun groupDao(): GroupDao
    abstract fun ruleDao(): RuleDao
    abstract fun latencySampleDao(): LatencySampleDao
    abstract fun ruleHitDao(): RuleHitDao

    companion object {
        const val DATABASE_NAME = "starveil.db"
    }
}
