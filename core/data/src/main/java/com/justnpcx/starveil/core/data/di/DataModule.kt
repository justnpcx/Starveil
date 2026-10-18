package com.justnpcx.starveil.core.data.di

import android.content.Context
import androidx.room.Room
import com.justnpcx.starveil.core.data.StarveilDatabase
import com.justnpcx.starveil.core.data.StarveilMigrations
import com.justnpcx.starveil.core.data.backup.BackupFileStore
import com.justnpcx.starveil.core.data.backup.ContentResolverBackupFileStore
import com.justnpcx.starveil.core.data.crypto.KeystoreLocalApiTokenStore
import com.justnpcx.starveil.core.data.crypto.KeystoreSecretStore
import com.justnpcx.starveil.core.data.crypto.LocalApiTokenStore
import com.justnpcx.starveil.core.data.dao.GroupDao
import com.justnpcx.starveil.core.data.dao.LatencySampleDao
import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.dao.RuleDao
import com.justnpcx.starveil.core.data.preferences.DataStorePreferencesRepository
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.geo.DohResolver
import com.justnpcx.starveil.core.data.ruleset.FileMainlandRuleSetStore
import com.justnpcx.starveil.core.data.ruleset.FileRulePackStore
import com.justnpcx.starveil.core.data.ruleset.MainlandRuleSetStore
import com.justnpcx.starveil.core.data.ruleset.RulePackStore
import java.io.File
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RoomGroupRepository
import com.justnpcx.starveil.core.data.repository.RoomNodeHealthRepository
import com.justnpcx.starveil.core.data.repository.RoomProfileRepository
import com.justnpcx.starveil.core.data.repository.RoomRuleRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Data-layer bindings. The database is built here and only here.
 *
 * RED LINES (see StarveilDatabase KDoc):
 * - NO `allowMainThreadQueries()`: every query runs off the main thread.
 * - NO `fallbackToDestructiveMigration()`: a failed migration is an
 *   explicit error, never a silent wipe.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideMainlandRuleSetStore(
        @ApplicationContext context: Context,
    ): MainlandRuleSetStore = FileMainlandRuleSetStore(
        directory = File(context.filesDir, "rulesets"),
        // Straight to logcat. A failed download is not worth a screen, but it
        // has to be findable when someone asks why the preset did nothing.
        warn = { android.util.Log.w("StarveilRuleSets", it) },
    )

    /**
     * Its own directory: a pack's file name is the upstream one, and the
     * mainland preset already owns `geosite-cn.srs` — sharing a directory would
     * make one feature's cleanup another's missing file.
     */
    @Provides
    @Singleton
    fun provideRulePackStore(
        @ApplicationContext context: Context,
    ): RulePackStore = FileRulePackStore(
        directory = File(context.filesDir, "rulepacks"),
        warn = { android.util.Log.w("StarveilRulePacks", it) },
    )

    /**
     * One resolver for the whole process: its concurrency limit and the cache
     * above it are only meaningful if every node lookup goes through the same
     * instance.
     */
    @Provides
    @Singleton
    fun provideDohResolver(): DohResolver = DohResolver(
        warn = { android.util.Log.w("StarveilDoh", it) },
    )

    @Provides
    @Singleton
    fun provideKeystoreSecretStore(
        @ApplicationContext context: Context,
    ): KeystoreSecretStore = KeystoreSecretStore(context)

    @Provides
    @Singleton
    fun provideLocalApiTokenStore(
        @ApplicationContext context: Context,
    ): LocalApiTokenStore = KeystoreLocalApiTokenStore(context)

    @Provides
    @Singleton
    fun provideBackupFileStore(
        @ApplicationContext context: Context,
    ): BackupFileStore = ContentResolverBackupFileStore(context)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        secretStore: KeystoreSecretStore,
    ): StarveilDatabase =
        Room.databaseBuilder(context, StarveilDatabase::class.java, StarveilDatabase.DATABASE_NAME)
            // Whole-database encryption: SQLCipher, passphrase wrapped by the
            // Android Keystore (see KeystoreSecretStore).
            .openHelperFactory(SupportOpenHelperFactory(secretStore.getOrCreatePassphrase()))
            .addMigrations(*StarveilMigrations.ALL)
            .build()

    @Provides
    fun provideProfileDao(database: StarveilDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideGroupDao(database: StarveilDatabase): GroupDao = database.groupDao()

    @Provides
    fun provideRuleDao(database: StarveilDatabase): RuleDao = database.ruleDao()

    @Provides
    fun provideLatencySampleDao(database: StarveilDatabase): LatencySampleDao =
        database.latencySampleDao()

    @Provides
    @Singleton
    fun provideProfileRepository(dao: ProfileDao): ProfileRepository =
        RoomProfileRepository(dao)

    @Provides
    @Singleton
    fun provideNodeHealthRepository(dao: LatencySampleDao): NodeHealthRepository =
        RoomNodeHealthRepository(dao)

    @Provides
    @Singleton
    fun provideGroupRepository(groupDao: GroupDao, profileDao: ProfileDao): GroupRepository =
        RoomGroupRepository(groupDao, profileDao)

    @Provides
    @Singleton
    fun provideRuleRepository(dao: RuleDao): RuleRepository = RoomRuleRepository(dao)

    @Provides
    @Singleton
    fun providePreferencesRepository(
        @ApplicationContext context: Context,
    ): PreferencesRepository = DataStorePreferencesRepository(context)
}
