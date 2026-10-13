package com.justnpcx.starveil.di

import android.content.Context
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.RealKernelBridge
import com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Kernel binding. M2 wires the real sing-box core; the Fake is kept in the
 * module for development and tests but is no longer the default.
 *
 * `prepareVpn` brings up the foreground VpnService (which owns the tun) before
 * the kernel starts. If the user has never granted VPN consent, the service
 * cannot establish a tun and the start fails — reported as
 * [com.justnpcx.starveil.core.kernel.KernelError.PermissionDenied].
 *
 * Note: @Provides (not @Binds) keeps :core:kernel free of DI annotations.
 */
@Module
@InstallIn(SingletonComponent::class)
object KernelModule {

    @Provides
    @Singleton
    fun provideKernelBridge(
        @ApplicationContext context: Context,
    ): KernelBridge = RealKernelBridge(
        prepareVpn = { StarveilVpnService.start(context) },
    )
}
