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

    /** Counts protect callbacks so only the first is logged. */
    private var protectCalls = 0

    /** Last interface name/index reported, so the diagnostic only fires on change. */
    private var lastDefaultInterface: String? = null

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
     * Deliberately does nothing.
     *
     * sing-box calls this to exempt each outbound socket from the tunnel, and
     * the usual answer is `VpnService.protect(fd)`. On the test device that
     * returns true but exempts nothing: the kernel's SYNs still land in the tun
     * (its RX counter rises in step with the dial rate) and every dial times
     * out — even to the LAN gateway. Worse, protect()'s socket mark overrode
     * the UID-based self-exclusion and pulled the socket *back* into the
     * tunnel. The whole process is excluded from the tunnel instead (see
     * StarveilVpnService.establishTun), so there is nothing to do per socket.
     */
    override fun autoDetectInterfaceControl(fd: Int) {
        if (protectCalls++ == 0) {
            diagnostic("autoDetectInterfaceControl: no per-socket protect (process is excluded)")
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
        // Report the current physical interface synchronously first, so no dial
        // can race the first callback.
        manager.activeNetwork?.let { reportDefaultInterface(manager, it, listener) }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        interfaceCallbacks.remove(listener)?.let { callback ->
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }

    /**
     * Reports the **physical** default interface, never our own tunnel.
     *
     * Once the tunnel is up, Android's default network *is* the VPN, so a naive
     * `registerDefaultNetworkCallback` hands us `tun0`. sing-box cannot resolve
     * that interface (`InterfaceFinder.ByIndex` fails), so its monitor is left
     * with no default interface — and `dialParallelInterface` then falls back to
     * binding every outbound socket to the physical device (`SO_BINDTODEVICE`),
     * which made every dial time out. Skipping the VPN and reporting the network
     * underneath it keeps the dialer on the plain "protect only" path.
     */
    private fun reportDefaultInterface(
        manager: ConnectivityManager,
        network: android.net.Network,
        listener: InterfaceUpdateListener,
    ) {
        val capabilities = manager.getNetworkCapabilities(network) ?: return
        val physical = if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            manager.allNetworks.firstOrNull { candidate ->
                candidate != network && manager.getNetworkCapabilities(candidate)?.let { it ->
                    !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
            } ?: return
        } else {
            network
        }
        val physicalCapabilities = manager.getNetworkCapabilities(physical) ?: return
        val link = manager.getLinkProperties(physical) ?: return
        val name = link.interfaceName ?: return
        // Null/0 means the app cannot see this interface (tun0 is hidden from
        // apps). Reporting it would make the kernel fail to resolve the
        // interface and fall back to a bound dial, so skip instead.
        val index = runCatching { java.net.NetworkInterface.getByName(name)?.index }.getOrNull() ?: return
        if (index == 0) return
        val reported = "$name ($index)"
        if (lastDefaultInterface != reported) {
            lastDefaultInterface = reported
            diagnostic("default interface -> $reported")
        }
        listener.updateDefaultInterface(
            name,
            index,
            /* expensive = */ !physicalCapabilities.hasCapability(
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
