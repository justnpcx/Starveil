package com.justnpcx.starveil.di

import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.Profile
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
    fun provideSubscriptionFormatRegistry(protocolRegistry: ProtocolRegistry): SubscriptionFormatRegistry =
        SubscriptionFormatRegistry.default { link -> resolveLink(protocolRegistry, link) }

    /**
     * Dispatches a share link to whichever protocol descriptor claims its
     * scheme. Keeps :core:subscription free of a dependency on :core:kernel.
     */
    private fun resolveLink(registry: ProtocolRegistry, link: String): Profile? {
        val scheme = link.substringBefore("://", "").lowercase()
        if (scheme.isBlank()) return null
        return registry.fromScheme(scheme)?.linkParser?.invoke(link)
    }
}
