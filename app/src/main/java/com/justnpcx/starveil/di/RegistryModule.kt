package com.justnpcx.starveil.di

import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.subscription.registry.SubscriptionFormatRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Registry assembly — the ONE place registries are built.
 *
 * Registries themselves carry no DI annotations (they are plain classes,
 * constructible in tests without Hilt). This module only wires the
 * default sets. Adding a protocol / subscription format = registering
 * one descriptor in the registry's `default()` factory (or here, for
 * app-specific additions) — never a new `when` branch.
 */
@Module
@InstallIn(SingletonComponent::class)
object RegistryModule {

    @Provides
    @Singleton
    fun provideProtocolRegistry(): ProtocolRegistry = ProtocolRegistry.default()

    @Provides
    @Singleton
    fun provideSubscriptionFormatRegistry(): SubscriptionFormatRegistry =
        SubscriptionFormatRegistry.default()
}
