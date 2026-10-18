package com.justnpcx.starveil.core.kernel.libbox

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
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
import java.net.InetSocketAddress

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
            // same sink through KernelDiagnostics. The bridge installs that sink
            // itself, because it also has to carry levels.
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
     * Which app owns a connection, for `package_name` rules.
     *
     * This is what makes per-app routing possible at all. The kernel asks for
     * every new connection (it always builds the process searcher on Android —
     * `route/router.go` sets `needFindProcess` unconditionally there), and it
     * caches the answer for 200 ms, so the binder call is once per connection
     * rather than once per packet.
     *
     * Answering with an empty owner — which this used to do — is not a
     * harmless stub: `PackageNameItem.Match` returns false when the package
     * list is empty, so every `package_name` rule would silently match nothing
     * and the traffic would fall through to the default route. A per-app rule
     * that quietly does nothing is worse than no per-app feature, because the
     * UI would claim it works.
     *
     * `getConnectionOwnerUid` needs API 29 (our minSdk) and
     * ACCESS_NETWORK_STATE (declared). It still throws on some ROMs, and an
     * unknown owner is a legitimate answer, so a failure is "unknown", not a
     * crash: UID 0 with no packages, exactly as before.
     */
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        val owner = ConnectionOwner()
        val uid = runCatching {
            connectivity?.getConnectionOwnerUid(
                ipProtocol,
                InetSocketAddress(sourceAddress, sourcePort),
                InetSocketAddress(destinationAddress, destinationPort),
            ) ?: Process.INVALID_UID
        }.onFailure {
            if (ownerLookups < 3) diagnostic("connection owner failed: ${it::class.java.simpleName}: ${it.message}")
        }.getOrDefault(Process.INVALID_UID)
        if (uid == Process.INVALID_UID) {
            // Logged because "no owner" is indistinguishable from "never
            // called" in the kernel's log, and the difference decides whether
            // the fault is here or in the config.
            if (ownerLookups++ < 3) {
                diagnostic(
                    "connection owner: unresolved $sourceAddress:$sourcePort -> " +
                        "$destinationAddress:$destinationPort proto=$ipProtocol",
                )
            }
            return owner
        }

        owner.userId = uid
        val packages = runCatching { context.packageManager.getPackagesForUid(uid) }
            .getOrNull()
        if (packages.isNullOrEmpty()) return owner
        owner.setAndroidPackageNames(StringArrayIterator(packages))
        if (ownerLookups++ < 3) {
            diagnostic("connection owner: uid=$uid packages=${packages.joinToString(",")}")
        }
        return owner
    }

    /** How many ownership lookups have been logged; see [findConnectionOwner]. */
    private var ownerLookups = 0

    private val connectivity: ConnectivityManager? by lazy {
        context.getSystemService(ConnectivityManager::class.java)
    }

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
            // The network the VPN runs over. Android does not expose the VPN's
            // underlying network to an app, so it is inferred: a validated
            // network first, and an unmetered one over a metered one — the same
            // preference the platform itself applies when choosing a default.
            //
            // `allNetworks` alone is not good enough and was actively wrong: it
            // has no order, and on a phone with Wi-Fi and mobile data both up it
            // handed back the cellular network while the user was on Wi-Fi. The
            // kernel binds every outbound dial to whatever is reported here, so
            // that mistake sends all traffic over mobile data.
            manager.allNetworks
                .filter { candidate ->
                    candidate != network && manager.getNetworkCapabilities(candidate)?.let { candidateCapabilities ->
                        !candidateCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                            candidateCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    } == true
                }
                .sortedWith(
                    compareByDescending<android.net.Network> { candidate ->
                        manager.getNetworkCapabilities(candidate)
                            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                    }.thenByDescending { candidate ->
                        manager.getNetworkCapabilities(candidate)
                            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
                    },
                )
                .firstOrNull()
                ?: return
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

    /**
     * False, and this is what makes `package_name` rules work at all.
     *
     * True sends the kernel's connection-owner lookup to its procfs searcher,
     * which cannot read another app's `/proc/<pid>/fd` on Android and fails
     * every time with "procfs: not found". The failure is logged at info and
     * otherwise ignored, so the rule is emitted, never matched, and the traffic
     * falls through to the default route — a per-app rule that silently does
     * nothing, which is the exact failure this whole lookup exists to avoid.
     *
     * False routes the same question to [findConnectionOwner] below, which uses
     * `ConnectivityManager.getConnectionOwnerUid` — the API Android provides
     * for precisely this.
     */
    override fun useProcFS(): Boolean = false
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

/**
 * A [StringIterator] over an array.
 *
 * The binding ships iterators for reading Go slices but not one for handing a
 * Kotlin array back, and `ConnectionOwner.setAndroidPackageNames` takes this
 * interface. `len()` is the remaining count: the Go side drains it with
 * `HasNext`/`Next` only (`iteratorToArray`), so the value is informational.
 */
private class StringArrayIterator(
    private val values: Array<String>,
) : StringIterator {
    private var index = 0

    override fun hasNext(): Boolean = index < values.size

    override fun len(): Int = values.size - index

    override fun next(): String = values[index++]
}
