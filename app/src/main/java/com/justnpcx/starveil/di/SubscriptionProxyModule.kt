package com.justnpcx.starveil.di

import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.subscription.SubscriptionProxy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The answer to "should this fetch go through the tunnel".
 *
 * Both halves of the answer live outside the subscription module: the
 * preference, and whether the kernel is actually up. A port with no kernel
 * behind it is worse than no port at all — the fetch would hang on a connect
 * that never completes — so the port is only offered while the tunnel runs.
 *
 * [runBlocking] is deliberate: the fetcher calls this from its IO dispatcher,
 * and DataStore answers from memory once it has loaded. The alternative is a
 * background mirror of the preference, and the stale window that comes with it.
 */
@Singleton
class SubscriptionRoute @Inject constructor(
    private val preferences: PreferencesRepository,
    private val kernel: KernelBridge,
) : SubscriptionProxy {

    override fun port(): Int? {
        if (kernel.state.value !is KernelState.Running) return null
        val enabled = runBlocking { preferences.subscriptionViaProxy.first() }
        return if (enabled) ConfigBuilder.SUBSCRIPTION_PROXY_PORT else null
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionProxyModule {

    @Provides
    @Singleton
    fun provideSubscriptionProxy(route: SubscriptionRoute): SubscriptionProxy = route
}
