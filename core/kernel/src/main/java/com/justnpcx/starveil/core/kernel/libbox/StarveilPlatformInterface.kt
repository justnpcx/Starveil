package com.justnpcx.starveil.core.kernel.libbox

import com.justnpcx.starveil.core.kernel.vpn.StarveilVpnService
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState

/**
 * Minimal `PlatformInterface` for Starveil.
 *
 * sing-box calls these back for platform services it cannot do itself. We
 * implement only what a plain VPN client needs and make everything else a
 * clear no-op or a loud "not used" — never a silent wrong answer.
 *
 * `openTun` is the one method that genuinely needs Android: it must return a
 * file descriptor from a `VpnService`. That is the remaining task-1 piece
 * (the foreground VpnService), so it is injected here rather than faked.
 *
 * Nothing here logs credentials; the interface never receives any.
 */
class StarveilPlatformInterface(
    /**
     * Named distinctly from the [openTun] override on purpose. A constructor
     * property called `openTun` made `override fun openTun(o) = openTun(o)`
     * resolve to the member function instead of the property, i.e. infinite
     * recursion — which surfaced on device as a bare
     * `StackOverflowError: stack size 1036KB` ("configure tun interface").
     */
    private val openTunProvider: (TunOptions) -> Int = { options ->
        // The tun is owned by the foreground VpnService; ask it for the fd.
        StarveilVpnService.current?.openTun(options)
            ?: throw IllegalStateException("VPN service is not running (no consent or not started)")
    },
) : PlatformInterface {

    override fun autoDetectInterfaceControl(fd: Int) {}

    override fun cancelNotification(identifier: String, typeID: Int) {}

    override fun checkPlatformShell() {}

    override fun clearDNSCache() {}

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun closeNeighborMonitor(listener: NeighborUpdateListener) {}

    override fun createBridge(options: BridgeOptions): BridgeSession =
        throw UnsupportedOperationException("bridge is not used")

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner? = null

    override fun getInterfaces(): NetworkInterfaceIterator? = null

    override fun includeAllNetworks(): Boolean = false

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun lookupSFTPServer(): String =
        throw UnsupportedOperationException("sftp is not used")

    override fun lookupUser(username: String): PlatformUser? = null

    override fun openShellSession(
        user: PlatformUser,
        command: String,
        environ: StringIterator,
        term: String,
        rows: Int,
        cols: Int,
    ): ShellSession = throw UnsupportedOperationException("shell is not used")

    override fun openTun(options: TunOptions): Int = openTunProvider(options)

    override fun readSystemSSHHostKey(): String =
        throw UnsupportedOperationException("ssh is not used")

    override fun readWIFIState(): WIFIState? = null

    override fun registerMyInterface(name: String) {}

    override fun sendNotification(notification: Notification) {}

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun startNeighborMonitor(listener: NeighborUpdateListener) {}

    override fun tailscaleHostname(): String = ""

    override fun underNetworkExtension(): Boolean = false

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = false

    override fun usePlatformBridge(): Boolean = false

    override fun usePlatformShell(): Boolean = false

    override fun useProcFS(): Boolean = true
}

/**
 * Minimal `CommandServerHandler`. We do not use the SSH agent or the system
 * proxy, and debug messages are dropped rather than logged (they can carry
 * request detail).
 */
class StarveilCommandServerHandler : CommandServerHandler {

    override fun connectSSHAgent(): Int = -1

    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus()

    override fun serviceReload() {}

    override fun serviceStop() {}

    override fun setSystemProxyEnabled(enabled: Boolean) {}

    override fun triggerNativeCrash() {}

    override fun writeDebugMessage(message: String) {}
}
