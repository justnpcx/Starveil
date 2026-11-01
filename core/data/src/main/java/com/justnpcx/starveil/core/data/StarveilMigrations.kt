package com.justnpcx.starveil.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migrations for [StarveilDatabase].
 *
 * RED LINE: there is no `fallbackToDestructiveMigration`. A failed migration
 * must surface as an explicit error and be recovered from an encrypted backup
 * (see [com.justnpcx.starveil.core.data.backup.DatabaseRecoveryPolicy]), never
 * by silently wiping the user's nodes, groups and rules — the Mirage-era
 * data-loss failure mode.
 */
object StarveilMigrations {
    /**
     * v1 → v2: the latency sample table behind the node health curve (§7.2).
     *
     * Purely additive — one `CREATE TABLE`, no data moved and no existing table
     * touched. That is deliberate: a migration that only adds a table has no
     * way to lose a row, and the failure modes worth fearing in a migration all
     * come from rewriting what is already there.
     *
     * The column list and primary key must match what Room expects for
     * [com.justnpcx.starveil.core.data.entity.LatencySampleEntity] exactly;
     * Room re-reads the schema after migrating and fails the open if it does
     * not. The exported schema at `schemas/…/2.json` is the authority.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `latency_samples` (
                    `profileId` INTEGER NOT NULL,
                    `atMs` INTEGER NOT NULL,
                    `latencyMs` INTEGER NOT NULL,
                    PRIMARY KEY(`profileId`, `atMs`)
                )
                """.trimIndent(),
            )
        }
    }

    /**
     * v2 → v3: `groups.region`, marking a group whose members are derived from
     * node names instead of stored on the profiles.
     *
     * Purely additive: one nullable column. Existing rows get NULL, which means
     * "an ordinary group whose members are its profiles" — exactly what they
     * already were.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `groups` ADD COLUMN `region` TEXT")
        }
    }

    /**
     * v3 → v4: `profiles.country`, the ISO code resolved from the node's
     * address.
     *
     * Additive and nullable: an existing node simply has no flag until the
     * resolver gets to it, and a node whose address cannot be looked up never
     * gets one — neither case is an error.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profiles` ADD COLUMN `country` TEXT")
        }
    }

    /**
     * v4 → v5: `latency_samples.weightTenths`, how much a sample counts.
     *
     * Additive with a default of 10, which is exactly what every row already
     * means: samples written before this column existed were all background
     * probes at full weight. No row changes value.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `latency_samples` ADD COLUMN `weightTenths` INTEGER NOT NULL DEFAULT 10",
            )
        }
    }

    /**
     * v5 → v6: `profiles.favorite`, the pin behind the Favorites filter.
     *
     * Additive with a default of 0: every existing node is not a favorite,
     * which is what "the feature did not exist" means. No row changes value.
     */
    /**
     * The front node, for chain proxying.
     *
     * Nullable and no default beyond null: every existing node is a single hop
     * until someone sets a front, and a node with no front must behave exactly
     * as it did before this column existed.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN frontNodeKey TEXT")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `profiles` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val ALL: Array<Migration> =
        arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
    )
}
