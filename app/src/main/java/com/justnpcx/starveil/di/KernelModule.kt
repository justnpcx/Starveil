package com.justnpcx.starveil.di

import android.content.Context
import com.justnpcx.starveil.core.kernel.FlowLog
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelPaths
import com.justnpcx.starveil.core.kernel.LogStream
import com.justnpcx.starveil.core.kernel.RealKernelBridge
import com.justnpcx.starveil.flows.KernelFlowLog
import com.justnpcx.starveil.core.kernel.libbox.StarveilPlatformInterface
import com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService
import com.justnpcx.starveil.leak.TunnelProbe
import com.justnpcx.starveil.leak.VpnServiceTunnelProbe
import com.justnpcx.starveil.latency.NodeProbe
import com.justnpcx.starveil.latency.TcpNodeProbe
import com.justnpcx.starveil.logs.KernelLogStream
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Kernel binding — the real sing-box core.
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
        // The platform interface needs a Context for the DNS resolver: on
        // Android sing-box resolves `local` through us, and we resolve on the
        // underlying network so a query cannot loop back into our own tunnel.
        platform = StarveilPlatformInterface(context),
        prepareVpn = { StarveilVpnService.start(context) },
        // Without this the tun outlives the kernel: the VPN icon stays in the
        // status bar and all app traffic keeps being routed into a dead tunnel.
        stopVpn = { StarveilVpnService.stop(context) },
    )

    /**
     * The Logs screen's view of the kernel's log lines. :app owns the adapter so
     * :feature:logs only sees [LogStream] and a :core:model type.
     */
    @Provides
    @Singleton
    fun provideLogStream(bridge: KernelBridge): LogStream = KernelLogStream(bridge)

    /**
     * The Rules screen's view of the kernel's routing decisions, on the same
     * terms: :app owns the adapter, the feature sees a bounded list.
     */
    @Provides
    @Singleton
    fun provideFlowLog(bridge: KernelBridge): FlowLog = KernelFlowLog(bridge)

    /**
     * What the leak self-check watches: whether the tunnel the Kill Switch
     * blocks with is standing. Bound here because only :app may name the
     * VpnService.
     */
    @Provides
    @Singleton
    fun provideTunnelProbe(): TunnelProbe = VpnServiceTunnelProbe()

    /**
     * The TCP handshake probe behind both the manual latency button and the
     * health sampler. One binding, so the number the button reports and the
     * number the curve draws cannot come from two implementations that drift.
     */
    @Provides
    @Singleton
    fun provideNodeProbe(): NodeProbe = TcpNodeProbe()
}
