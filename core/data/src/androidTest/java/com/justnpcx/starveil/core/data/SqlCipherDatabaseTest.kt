package com.justnpcx.starveil.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnpcx.starveil.core.data.crypto.KeystoreSecretStore
import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.entity.GroupEntity
import com.justnpcx.starveil.core.data.entity.ProfileEntity
import com.justnpcx.starveil.core.data.entity.RuleEntity
import com.justnpcx.starveil.core.data.repository.RoomProfileRepository
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.RealityOptions
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.TlsOptions
import com.justnpcx.starveil.core.model.VlessSettings
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A database at the same version as [StarveilDatabase] but with no migration
 * registered, used only to force a migration failure against an older file.
 *
 * Its entity set deliberately differs from the real one: what is under test is
 * the *policy* (a missing migration must throw rather than wipe), not any
 * particular migration. The real migrations are covered by
 * [StarveilMigrationTest].
 */
@Database(
    entities = [ProfileEntity::class, GroupEntity::class, RuleEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NoMigrationDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}

/**
 * Real-database verification for the SQLCipher integration.
 *
 * The in-memory fake DAO used by the JVM tests cannot exercise SQLCipher at
 * all (it never opens a database), so this instrumented test is the only place
 * the encryption, the Keystore passphrase and the migration-failure policy are
 * actually proven.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherDatabaseTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        System.loadLibrary("sqlcipher")
    }

    private fun freshDbName(): String = "test-${System.nanoTime()}.db"

    private fun openV1(name: String, passphrase: ByteArray): StarveilDatabase =
        Room.databaseBuilder(context, StarveilDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()

    private val node = Profile(
        name = "reality-node",
        type = ProfileType.VLESS,
        server = "r.example.com",
        port = 443,
        settings = VlessSettings(
            uuid = "11111111-2222-3333-4444-555555555555",
            tls = TlsOptions(
                security = SecurityMode.REALITY,
                sni = "r.example.com",
                reality = RealityOptions(publicKey = "PBK", shortId = "ab12", mlKem = true),
            ),
        ),
    )

    @Test
    fun keystore_passphrase_is_stable_and_non_empty() {
        val store = KeystoreSecretStore(context)
        val first = store.getOrCreatePassphrase()
        val second = store.getOrCreatePassphrase()
        assertTrue(first.isNotEmpty())
        assertArrayEquals(first, second)
    }

    @Test
    fun database_file_is_encrypted_and_round_trips_credentials() = runBlocking {
        val name = freshDbName()
        val passphrase = KeystoreSecretStore(context).getOrCreatePassphrase()

        val db = openV1(name, passphrase)
        val id = RoomProfileRepository(db.profileDao()).upsert(node)
        db.close()

        // The file must not start with the plaintext SQLite magic.
        val header = context.getDatabasePath(name).readBytes().copyOfRange(0, 16)
        assertFalse(
            "database is not encrypted",
            String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3"),
        )

        // Reopen with the same passphrase: credentials must come back intact.
        val reopened = openV1(name, passphrase)
        val loaded = RoomProfileRepository(reopened.profileDao()).getById(id)
        reopened.close()
        assertNotNull(loaded)
        assertEquals(node.settings, loaded!!.settings)
    }

    @Test
    fun a_wrong_passphrase_cannot_open_the_database() {
        val name = freshDbName()
        val passphrase = KeystoreSecretStore(context).getOrCreatePassphrase()
        runBlocking {
            val db = openV1(name, passphrase)
            RoomProfileRepository(db.profileDao()).upsert(node)
            db.close()
        }

        var failed = false
        try {
            val wrong = openV1(name, ByteArray(32) { 7 })
            runBlocking { RoomProfileRepository(wrong.profileDao()).observeAll() }
            // Touch the DB so the open actually happens.
            runBlocking { RoomProfileRepository(wrong.profileDao()).getById(1L) }
            wrong.close()
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("opening with a wrong passphrase should fail", failed)
    }

    @Test
    fun a_failed_migration_throws_and_does_not_wipe_the_file() = runBlocking {
        val name = freshDbName()
        val passphrase = KeystoreSecretStore(context).getOrCreatePassphrase()

        // Create a v1 database with data.
        val v1 = openV1(name, passphrase)
        RoomProfileRepository(v1.profileDao()).upsert(node)
        v1.close()
        val sizeBefore = context.getDatabasePath(name).length()
        assertTrue(sizeBefore > 0)

        // Open it at the next version with no migration registered: Room must
        // throw, and must NOT fall back to destroying the user's data.
        var threw = false
        try {
            val v2 = Room.databaseBuilder(context, NoMigrationDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
            v2.profileDao().getById(1L)
            v2.close()
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("expected a migration failure", threw)
        assertTrue("database file was deleted", context.getDatabasePath(name).exists())
    }
}
