package com.justnpcx.starveil.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every migration in [StarveilMigrations], validated against the exported
 * schema.
 *
 * `runMigrationsAndValidate` re-reads the schema after migrating and fails if
 * it does not match what Room expects, so this is the check that the migration
 * SQL and the entity actually agree — the one thing a compile cannot prove.
 *
 * Runs against a plain SQLite file, not SQLCipher: the migration is DDL, and
 * the encryption is orthogonal to whether the DDL is right (that half is
 * covered by [SqlCipherDatabaseTest]).
 *
 * NOTE: instrumented, so CI does not run it — CI is assembleDebug +
 * unit tests + lint, none of which touch androidTest. It is device-verified.
 */
@RunWith(AndroidJUnit4::class)
class StarveilMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StarveilDatabase::class.java,
    )

    @Test
    fun migration_1_to_2_keeps_every_row_and_adds_the_sample_table() {
        val name = "migration-${System.nanoTime()}.db"

        // The database exactly as v1 shipped it, with a row in each table.
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO profiles (name, type, server, port, latencyMs, groupId, settingsJson) " +
                    "VALUES ('reality-node', 'VLESS', 'r.example.com', 443, 42, 0, '{}')",
            )
            db.execSQL(
                "INSERT INTO groups (name, mode, subscriptionUrl) " +
                    "VALUES ('sub', 'URL_TEST', NULL)",
            )
            db.execSQL(
                "INSERT INTO rules (matcher, action, sort_order, enabled) " +
                    "VALUES ('example.com', 'PROXY', 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(name, 2, true, *StarveilMigrations.ALL).use { db ->
            // Nothing was lost. This is the whole reason there is no
            // destructive fallback.
            assertEquals(1, db.rowCount("profiles"))
            assertEquals(1, db.rowCount("groups"))
            assertEquals(1, db.rowCount("rules"))

            // The new table is there and writable, and stores a timeout as a
            // negative latency rather than refusing it.
            db.execSQL("INSERT INTO latency_samples (profileId, atMs, latencyMs) VALUES (1, 1000, 42)")
            db.execSQL("INSERT INTO latency_samples (profileId, atMs, latencyMs) VALUES (1, 2000, -1)")
            assertEquals(2, db.rowCount("latency_samples"))
            assertEquals(-1, db.scalarInt("SELECT latencyMs FROM latency_samples WHERE atMs = 2000"))
        }
    }

    @Test
    fun migration_2_to_3_keeps_every_row_and_adds_the_region_column() {
        val name = "migration-v3-${System.nanoTime()}.db"

        helper.createDatabase(name, 2).use { db ->
            db.execSQL(
                "INSERT INTO groups (name, mode, subscriptionUrl) VALUES ('sub', 'URL_TEST', NULL)",
            )
        }

        helper.runMigrationsAndValidate(name, 3, true, *StarveilMigrations.ALL).use { db ->
            assertEquals(1, db.rowCount("groups"))

            // Every existing group is an ordinary group: NULL means "members are
            // the profiles", which is exactly what it was before the column.
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM groups WHERE region IS NULL"))

            // And a derived group can be written and read back.
            db.execSQL(
                "INSERT INTO groups (name, mode, subscriptionUrl, region) " +
                    "VALUES ('香港', 'URL_TEST', NULL, 'HONG_KONG')",
            )
            assertEquals(
                1,
                db.scalarInt("SELECT COUNT(*) FROM groups WHERE region = 'HONG_KONG'"),
            )
        }
    }

    @Test
    fun migration_3_to_4_keeps_every_row_and_adds_the_country_column() {
        val name = "migration-v4-${System.nanoTime()}.db"

        helper.createDatabase(name, 3).use { db ->
            db.execSQL(
                "INSERT INTO profiles (name, type, server, port, latencyMs, groupId, settingsJson) " +
                    "VALUES ('node', 'VLESS', 'a.example.com', 443, -1, 0, '{}')",
            )
        }

        helper.runMigrationsAndValidate(name, 4, true, *StarveilMigrations.ALL).use { db ->
            assertEquals(1, db.rowCount("profiles"))

            // Every existing node is unresolved, which is a working node without
            // a flag — not an error, and not a reason to drop the row.
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM profiles WHERE country IS NULL"))

            db.execSQL("UPDATE profiles SET country = 'US' WHERE id = 1")
            assertEquals("US", db.scalarString("SELECT country FROM profiles WHERE id = 1"))
        }
    }

    @Test
    fun the_composite_key_makes_a_re_probe_a_replace() {
        val name = "migration-pk-${System.nanoTime()}.db"
        helper.createDatabase(name, 1).use { }

        helper.runMigrationsAndValidate(name, 2, true, *StarveilMigrations.ALL).use { db ->
            // Room's REPLACE strategy is INSERT OR REPLACE; the same node at
            // the same instant is the same observation, so it replaces.
            db.execSQL("INSERT OR REPLACE INTO latency_samples (profileId, atMs, latencyMs) VALUES (1, 1000, 42)")
            db.execSQL("INSERT OR REPLACE INTO latency_samples (profileId, atMs, latencyMs) VALUES (1, 1000, 99)")
            // Two *nodes* at the same instant are two rows — the key is
            // composite, not just the timestamp.
            db.execSQL("INSERT OR REPLACE INTO latency_samples (profileId, atMs, latencyMs) VALUES (2, 1000, 7)")

            assertEquals(2, db.rowCount("latency_samples"))
            assertEquals(
                99,
                db.scalarInt("SELECT latencyMs FROM latency_samples WHERE profileId = 1 AND atMs = 1000"),
            )
        }
    }
}

private fun SupportSQLiteDatabase.rowCount(table: String): Int =
    scalarInt("SELECT COUNT(*) FROM $table")

private fun SupportSQLiteDatabase.scalarInt(sql: String): Int =
    query(sql).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

private fun SupportSQLiteDatabase.scalarString(sql: String): String? =
    query(sql).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
