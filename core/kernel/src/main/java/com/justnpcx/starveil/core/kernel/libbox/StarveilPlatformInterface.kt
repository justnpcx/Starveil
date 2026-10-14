package com.justnpcx.starveil.core.kernel.libbox

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
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
    private val context: android.content.Context,
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

    /**
     * Backs the config's `local` DNS server. On Android there is no
     * resolv.conf, so without this the kernel dials `[::1]:53` and the
     * outbound's own server address never resolves.
     */
    private val localDnsTransport = StarveilLocalDnsTransport(context, ::diagnostic)

    /** Counts protect calls so the first few are logged and then it goes quiet. */
    private var protectLogs = 0

    /** Keeps the network callbacks alive, and lets close() unregister them. */
    private val interfaceCallbacks =
        java.util.concurrent.ConcurrentHashMap<InterfaceUpdateListener, ConnectivityManager.NetworkCallback>()

    /**
     * Where kernel-side diagnostics go, on top of logcat.
     *
     * adb logcat is unreliable on some ROMs (the vivo test device among them),
     * so the bridge attaches a sink that also feeds the Logs screen. Set once,
     * by [RealKernelBridge].
     */
    var onDiagnostic: ((String) -> Unit)? = null
        set(value) {
            field = value
            // The VpnService has no way to receive a callback, so it reads the
            // same sink through KernelDiagnostics.
            KernelDiagnostics.sink = value
        }

    internal fun diagnostic(message: String) = KernelDiagnostics.log(message)

    /**
     * Called by the kernel for each socket it opens, so the socket can be
     * excluded from our own tunnel. VpnService.protect is the documented way to
     * do that; without it the proxy's own connection to its server goes into
     * the tun and times out.
     */
    override fun autoDetectInterfaceControl(fd: Int) {
        val service = StarveilVpnService.current
        val protected = service?.protectSocket(fd)
        // Diagnostic: whether the kernel is asking at all, and whether protect
        // succeeds. A socket that is not protected is routed back into our own
        // tunnel and its dial times out with nothing else in the log.
        if (protectLogs < PROTECT_LOG_LIMIT || protected != true) {
            protectLogs++
            diagnostic("protect(fd=$fd) -> ${protected ?: "no VpnService"}")
        }
    }

    override fun cancelNotification(identifier: String, typeID: Int) {}

    override fun checkPlatformShell() {}

    override fun clearDNSCache() {}

    override fun closeNeighborMonitor(listener: NeighborUpdateListener) {}

    override fun createBridge(options: BridgeOptions): BridgeSession =
        throw UnsupportedOperationException("bridge is not used")

    /**
     * Must not return null. The Go side reads the result without a nil check
     * (`service.go:226` does `result.UserId`), so returning null aborts the
     * process with a nil-pointer panic the moment the router asks which app
     * owns a connection. UserId 0 is the honest "unknown" answer.
     */
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner = ConnectionOwner()

    /**
     * Not optional on this fork: the dialer always asks for the platform's
     * network interfaces (see StarveilNetworkInterfaces), and returning null
     * fails every proxied connection with "no available network interface".
     */
    override fun getInterfaces(): NetworkInterfaceIterator? =
        listPlatformNetworkInterfaces(context, ::diagnostic)

    override fun includeAllNetworks(): Boolean = false

    override fun localDNSTransport(): LocalDNSTransport = localDnsTransport

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

    /**
     * Reports the default interface to the kernel.
     *
     * Not optional on this fork: the dialer picks an interface from what this
     * monitor has told it (`selectInterfaces`), and with no updates every
     * proxied connection fails with "no available network interface" before it
     * dials — which is exactly what the Logs screen showed on device.
     */
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) =
                reportDefaultInterface(manager, network, listener)

            override fun onCapabilitiesChanged(
                network: android.net.Network,
                capabilities: NetworkCapabilities,
            ) = reportDefaultInterface(manager, network, listener)

            override fun onLost(network: android.net.Network) {
                manager.activeNetwork?.let { reportDefaultInterface(manager, it, listener) }
            }
        }
        interfaceCallbacks[listener] = callback
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        interfaceCallbacks.remove(listener)?.let { callback ->
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }

    private fun reportDefaultInterface(
        manager: ConnectivityManager,
        network: android.net.Network,
        listener: InterfaceUpdateListener,
    ) {
        val capabilities = manager.getNetworkCapabilities(network) ?: return
        val link = manager.getLinkProperties(network) ?: return
        val name = link.interfaceName ?: return
        val index = runCatching { java.net.NetworkInterface.getByName(name)?.index }.getOrNull() ?: 0
        listener.updateDefaultInterface(
            name,
            index,
            /* expensive = */ !capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
            ),
            /* constrained = */ false,
        )
    }

    override fun startNeighborMonitor(listener: NeighborUpdateListener) {}

    override fun tailscaleHostname(): String = ""

    override fun underNetworkExtension(): Boolean = false

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun usePlatformBridge(): Boolean = false

    override fun usePlatformShell(): Boolean = false

    override fun useProcFS(): Boolean = true

    private companion object {
        const val PROTECT_LOG_LIMIT = 3
    }
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
