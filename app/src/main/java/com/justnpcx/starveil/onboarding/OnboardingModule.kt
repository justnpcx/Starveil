package com.justnpcx.starveil.onboarding

import com.justnpcx.starveil.core.subscription.LinkImporter
import com.justnpcx.starveil.feature.dashboard.ConnectionController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the two ports onboarding uses to the real implementations.
 *
 * The screen is in :app, which already depends on both features, so this is
 * where they are allowed to meet.
 */
@Module
@InstallIn(SingletonComponent::class)
object OnboardingModule {

    @Provides
    @Singleton
    fun provideNodeImporter(importer: LinkImporter): NodeImporter =
        NodeImporter { text -> importer.import(text) }

    @Provides
    @Singleton
    fun provideConnectNow(connection: ConnectionController): ConnectNow =
        ConnectNow { connection.connect() }
}
