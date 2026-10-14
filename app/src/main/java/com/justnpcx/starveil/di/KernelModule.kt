package com.justnpcx.starveil.di

import android.content.Context
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelPaths
import com.justnpcx.starveil.core.kernel.RealKernelBridge
import com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
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
        // libbox needs real, writable roots; empty paths make Libbox.setup
        // throw (it runs MkdirAll on them). See KernelPaths.
        paths = KernelPaths(
            basePath = File(context.filesDir, "libbox").absolutePath,
            workingPath = context.filesDir.absolutePath,
            tempPath = context.cacheDir.absolutePath,
        ),
        prepareVpn = { StarveilVpnService.start(context) },
        // KernelBridge.urlTest takes a "link", but this app is Profile-centric
        // (a Profile carries no share link), so the argument is read as an
        // outbound tag. Identity is therefore the honest mapping: callers pass
        // the tag ConfigBuilder assigned ('proxy' for a single-node config,
        // 'proxy-<n>' for a multi-node one). Documented in docs/04.
        linkToTag = { tag -> tag },
    )
}
