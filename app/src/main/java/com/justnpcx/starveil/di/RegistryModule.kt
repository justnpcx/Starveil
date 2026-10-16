package com.justnpcx.starveil.di

import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.subscription.LinkExporter
import com.justnpcx.starveil.core.subscription.LinkResolver
import com.justnpcx.starveil.core.subscription.registry.SubscriptionFormatRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
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

    /** Pure config assembly; given the registry so it can build outbounds. */
    @Provides
    @Singleton
    fun provideConfigBuilder(registry: ProtocolRegistry): ConfigBuilder = ConfigBuilder(registry)

    /** Lets :core:subscription parse share links without depending on :core:kernel. */
    @Provides
    @Singleton
    fun provideLinkResolver(registry: ProtocolRegistry): LinkResolver =
        LinkResolver { link -> resolveLink(registry, link) }

    /** The other half: a node back into a share link, for the Share sheet. */
    @Provides
    @Singleton
    fun provideLinkExporter(registry: ProtocolRegistry): LinkExporter =
        LinkExporter { profile -> registry.writeLink(profile) }

    @Provides
    @Singleton
    fun provideSubscriptionFormatRegistry(protocolRegistry: ProtocolRegistry): SubscriptionFormatRegistry =
        SubscriptionFormatRegistry.default { link -> resolveLink(protocolRegistry, link) }

    /**
     * Dispatches pasted text to whichever protocol descriptor claims it.
     *
     * A share link is matched by its scheme. Text with no scheme — a WireGuard
     * `.conf` — goes to the descriptors that opted into sniffing instead.
     * Keeps :core:subscription free of a dependency on :core:kernel.
     */
    private fun resolveLink(registry: ProtocolRegistry, text: String): Profile? {
        val trimmed = text.trim()
        val scheme = trimmed.substringBefore("://", "")
        // A real scheme is short and has no whitespace. Without that guard a
        // `.conf` whose body happens to contain "://" would be read as one.
        val hasScheme = scheme.isNotBlank() &&
            scheme.length < SCHEME_MAX_LENGTH &&
            scheme.none { it.isWhitespace() || it == '[' || it == ']' }
        if (hasScheme) {
            return registry.fromScheme(scheme)?.linkParser?.invoke(trimmed)
        }
        return registry.all().firstNotNullOfOrNull { descriptor ->
            if (descriptor.sniff(trimmed)) descriptor.linkParser(trimmed) else null
        }
    }

    /** Longest plausible URI scheme; anything longer is not one. */
    private const val SCHEME_MAX_LENGTH = 32
}

/**
 * The nav graph builds one editor route per protocol from the registry, so it
 * needs the registry itself. :app is allowed to see :core:kernel; the feature
 * modules are not, which is why the routes are assembled here.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProtocolRegistryEntryPoint {
    fun protocolRegistry(): ProtocolRegistry
}
