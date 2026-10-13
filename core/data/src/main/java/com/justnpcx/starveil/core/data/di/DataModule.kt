package com.justnpcx.starveil.core.data.di

import android.content.Context
import androidx.room.Room
import com.justnpcx.starveil.core.data.StarveilDatabase
import com.justnpcx.starveil.core.data.dao.GroupDao
import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.dao.RuleDao
import com.justnpcx.starveil.core.data.preferences.DataStorePreferencesRepository
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RoomGroupRepository
import com.justnpcx.starveil.core.data.repository.RoomProfileRepository
import com.justnpcx.starveil.core.data.repository.RoomRuleRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    fun provideDatabase(@ApplicationContext context: Context): StarveilDatabase =
        Room.databaseBuilder(context, StarveilDatabase::class.java, StarveilDatabase.DATABASE_NAME)
            .build()

    @Provides
    fun provideProfileDao(database: StarveilDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideGroupDao(database: StarveilDatabase): GroupDao = database.groupDao()

    @Provides
    fun provideRuleDao(database: StarveilDatabase): RuleDao = database.ruleDao()

    @Provides
    @Singleton
    fun provideProfileRepository(dao: ProfileDao): ProfileRepository =
        RoomProfileRepository(dao)

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
