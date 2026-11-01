package com.justnpcx.starveil.core.subscription.di

import android.content.Context
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.subscription.Clock
import com.justnpcx.starveil.core.subscription.HttpUrlConnectionFetcher
import com.justnpcx.starveil.core.subscription.SubscriptionProxy
import com.justnpcx.starveil.core.subscription.SubscriptionFetcher
import com.justnpcx.starveil.core.subscription.SubscriptionHealthSink
import com.justnpcx.starveil.core.subscription.SubscriptionRefreshScheduler
import com.justnpcx.starveil.core.subscription.WorkManagerRefreshScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionModule {

    @Provides
    @Singleton
    fun provideSubscriptionFetcher(proxy: SubscriptionProxy): SubscriptionFetcher =
        HttpUrlConnectionFetcher(proxy)

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideSubscriptionRefreshScheduler(
        @ApplicationContext context: Context,
    ): SubscriptionRefreshScheduler = WorkManagerRefreshScheduler(context)

    @Provides
    @Singleton
    fun provideSubscriptionHealthSink(preferences: PreferencesRepository): SubscriptionHealthSink =
        object : SubscriptionHealthSink {
            override suspend fun recordSuccess(atEpochMs: Long) =
                preferences.recordSubscriptionSuccess(atEpochMs)

            override suspend fun recordFailure(atEpochMs: Long, reason: String) =
                preferences.recordSubscriptionFailure(atEpochMs, reason)
        }
}
