package com.justnpcx.starveil.core.data

import androidx.room.migration.Migration

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
    val ALL: Array<Migration> = emptyArray()
}
