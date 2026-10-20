package com.justnpcx.starveil.core.kernel.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import com.justnpcx.starveil.core.kernel.R
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions

/**
 * Foreground VpnService that owns the tun device.
 *
 * The kernel never creates the tun itself: it calls
 * `PlatformInterface.openTun(TunOptions)`, and this service answers by
 * building a tun from those options and handing back the file descriptor
 * (see docs/04-libbox-build.md). That is the piece that makes real traffic
 * flow.
 *
 * Consent: the Activity must have called `VpnService.prepare()` and got a
 * null intent first; `establish()` returning null here means consent is
 * missing, which the bridge maps to `KernelError.PermissionDenied`.
 */
class StarveilVpnService : VpnService() {

    /**
     * The service's own handle on the tun.
     *
     * The kernel gets a *duplicate* descriptor; this one stays open for as
     * long as the service lives. That is what lets a block stop the kernel
     * without destroying the interface, and lets the next kernel reattach to
     * the same tun.
     */
    private var tun: ParcelFileDescriptor? = null

    /**
     * Every descriptor handed to a kernel.
     *
     * A set, not one value: a block keeps the tun and a reconnect hands the new
     * kernel another duplicate, and the old kernel never closed its own — the
     * interface outlived a stop and kept the VpnService bound, leaving the
     * phone with no network (measured on the vivo).
     */
    private val kernelFds = mutableSetOf<Int>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires an explicit type; without it the platform
            // throws MissingForegroundServiceTypeException. The manifest
            // declares specialUse (see this module's AndroidManifest.xml).
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        current = this
        trackUnderlyingNetwork()
        return START_STICKY
    }

    /** The network the tunnel sits on; declared to the system while we run. */
    private var underlying: android.net.Network? = null
    private var callbackSlot: NetworkCallbackSlot? = null

    /**
     * Tracks the network the tunnel sits on, and declares it to the system.
     *
     * Must run *before* the tunnel exists: once the VPN is up the default
     * network is the VPN itself, and there would be no real network left to
     * name. Declaring it is what gives traffic excluded from the tunnel (our
     * own package, via addDisallowedApplication) a network to use.
     */
    private fun trackUnderlyingNetwork() {
        val manager = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        manager.activeNetwork?.let { active ->
            if (!isVpn(manager, active)) {
                underlying = active
                KernelDiagnostics.log(
                    "underlying network: ${manager.getLinkProperties(active)?.interfaceName}",
                )
            }
        }
        if (callbackSlot?.isActive == true) return
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                if (isVpn(manager, network)) return
                underlying = network
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onLost(network: android.net.Network) {
                if (underlying == network) underlying = null
            }
        }
        callbackSlot = NetworkCallbackSlot(
            register = {
                runCatching { manager.registerDefaultNetworkCallback(callback) }
                    .onFailure {
                        KernelDiagnostics.logError(
                            "network callback registration failed: ${it.javaClass.simpleName}",
                        )
                    }
                    .isSuccess
            },
            unregister = {
                runCatching { manager.unregisterNetworkCallback(callback) }
                    .onFailure {
                        KernelDiagnostics.logError(
                            "network callback unregister failed: ${it.javaClass.simpleName}",
                        )
                    }
            },
        ).also { it.acquire() }
    }

    /**
     * Releases the default-network callback.
     *
     * Called from both [onRevoke] and [onDestroy]: the first path is the system
     * taking the VPN away, the second is a normal stop, and a revoke reaches
     * both. The slot makes the second call a no-op.
     */
    private fun releaseNetworkCallback() {
        val released = callbackSlot?.release() == true
        if (released) {
            // Paired with the registration above: one line each way makes a
            // leak visible in the Logs screen instead of only in dumpsys.
            KernelDiagnostics.log("network callback released")
        }
        callbackSlot = null
        underlying = null
    }

    private fun isVpn(
        manager: android.net.ConnectivityManager,
        network: android.net.Network,
    ): Boolean = manager.getNetworkCapabilities(network)
        ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true

    override fun onRevoke() {
        // The system revoked our VPN: tear down so the kernel does not keep
        // running against a dead tun. The callback goes first — a callback
        // left registered here outlives the service that owns it.
        releaseNetworkCallback()
        closeTun()
        current = null
        stopSelf()
    }

    override fun onDestroy() {
        KernelDiagnostics.log("VpnService onDestroy; releasing tun")
        releaseNetworkCallback()
        closeTun()
        current = null
        super.onDestroy()
    }

    /**
     * Builds the tun from the kernel's options and returns its file descriptor.
     *
     * Ownership passes to the kernel, so the fd is detached from the PFD here.
     *
     * Every failure is re-thrown with its class, because this is the one place
     * a platform/ROM failure reaches the user as a kernel start error, and a
     * bare message cannot be attributed to us or to the framework.
     */
    fun openTun(options: TunOptions): Int {
        return try {
            establishTun(options)
        } catch (t: Throwable) {
            throw IllegalStateException(
                "openTun failed [${t::class.java.name}] mtu=${options.mtu}: ${t.message}",
                t,
            )
        }
    }

    private fun establishTun(options: TunOptions): Int {
        // A block keeps this service and its tun alive. The next kernel gets a
        // duplicate of the same interface instead of a new one, so a block →
        // reconnect keeps the VPN session (and its consent) intact. Nothing
        // reads the tun while no kernel is attached, which is the block.
        tun?.let { existing ->
            KernelDiagnostics.log("openTun: reusing tun fd ${existing.fd}")
            val fd = ParcelFileDescriptor.dup(existing.fileDescriptor).detachFd()
            kernelFds.add(fd)
            return fd
        }
        closeTun()

        val builder = Builder()
            .setSession(SESSION_NAME)
            .setMtu(options.mtu)

        // Counted rather than logged twice: these iterators are one-shot.
        var v4Routes = 0
        var v6Routes = 0
        var v4Addrs = 0
        var v6Addrs = 0
        var dnsServers = 0

        if (options.autoRoute) {
            // The *Range* accessors carry the ranges the kernel computed for
            // auto_route. RouteAddress carries the config's explicit
            // route_address and is empty unless the config names them — using
            // it leaves the tun with no routes, so nothing is ever captured
            // and the kernel's traffic counter stays at zero.
            options.inet4RouteRange?.forEach {
                v4Routes++
                builder.addRoute(it.address(), it.prefix())
            }
            options.inet6RouteRange?.forEach {
                v6Routes++
                builder.addRoute(it.address(), it.prefix())
            }
        }
        // Explicit routes, when the config names them.
        options.inet4RouteAddress?.forEach {
            v4Routes++
            builder.addRoute(it.address(), it.prefix())
        }
        options.inet6RouteAddress?.forEach {
            v6Routes++
            builder.addRoute(it.address(), it.prefix())
        }
        options.inet4Address?.forEach {
            v4Addrs++
            builder.addAddress(it.address(), it.prefix())
        }
        options.inet6Address?.forEach {
            v6Addrs++
            builder.addAddress(it.address(), it.prefix())
        }

        options.dnsServerAddress?.forEach {
            dnsServers++
            builder.addDnsServer(it)
        }
        // dnsMode is advisory (sing-box resolves it); we do not set a resolver
        // from it because Builder has no equivalent.

        options.includePackage?.forEach { runCatching { builder.addAllowedApplication(it) } }
        options.excludePackage?.forEach { runCatching { builder.addDisallowedApplication(it) } }

        // Exclude ourselves. The kernel runs in this process, so its dial to
        // the node would otherwise be routed back into the tunnel it is trying
        // to build.
        //
        // This is done by UID, and NOT with VpnService.protect(): on the test
        // device protect() returns true but does not keep the socket out — the
        // kernel's SYNs still land in the tun (the tun's RX counter rises in
        // step with the dial rate) and every dial times out, even to the LAN
        // gateway. Worse, protect()'s socket mark overrode the UID exclusion
        // and pulled the socket back into the tunnel. So: no protect(), and
        // route the whole process around the tunnel instead.
        // Do NOT exclude the process by UID. The system/mixed tun stacks run
        // TCP through a kernel listener owned by this app; a UID exclusion
        // sends that listener's SYN-ACKs outside the tunnel, so the handshake
        // never completes (SYN_SENT on the client, SYN_RECV on the listener).
        // Exemption is per socket instead: libbox calls
        // autoDetectInterfaceControl for every outbound dial and we protect
        // exactly that fd.

        // Diagnostic: what the kernel actually asked for. Without routes (or
        // without an address) the tun is established but captures nothing, and
        // the kernel's own traffic counter then reports zero forever.
        android.util.Log.i(
            "StarveilKernel",
            "openTun: mtu=${options.mtu} autoRoute=${options.autoRoute} " +
                "v4addr=$v4Addrs v4routes=$v4Routes v6addr=$v6Addrs v6routes=$v6Routes dns=$dnsServers",
        )

        underlying?.let { builder.setUnderlyingNetworks(arrayOf(it)) }

        val descriptor = builder.establish()
            ?: throw IllegalStateException("VPN consent not granted")

        tun = descriptor
        underlying?.let { setUnderlyingNetworks(arrayOf(it)) }
        // The service keeps this descriptor and the kernel gets a duplicate:
        // when the kernel closes its copy, the interface stays up (that is
        // what makes the blackhole block possible), and a later start can
        // hand out another duplicate of the same tun.
        val fd = ParcelFileDescriptor.dup(descriptor.fileDescriptor).detachFd()
        kernelFds.add(fd)
        return fd
    }

    /** Whether a tun is established and kept by this service. */
    val hasTun: Boolean get() = tun != null

    /** Releases the tun. Called on teardown, and from [stop] before stopping. */
    internal fun closeTun() {
        tun?.let {
            KernelDiagnostics.log("VpnService closing tun fd ${it.fd}")
            runCatching { it.close() }
        }
        tun = null
        closeKernelFds()
    }

    /**
     * Closes every descriptor the kernel was handed.
     *
     * It has to be us: after the kernel's service closes, the fd is still open
     * and the interface outlives the stop, which keeps the VpnService bound and
     * leaves the phone with no network. The Go side does not close it, so this
     * is not a double close in practice; if that ever changes, this becomes
     * one — the alternative is a guaranteed leak.
     */
    private fun closeKernelFds() {
        if (kernelFds.isEmpty()) return
        KernelDiagnostics.log("VpnService closing ${kernelFds.size} kernel tun fd(s)")
        kernelFds.forEach { runCatching { ParcelFileDescriptor.adoptFd(it).close() } }
        kernelFds.clear()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        // The launch intent rather than a class reference: this module does not
        // depend on :app, and it should not start to just to open a screen.
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(this, REQUEST_OPEN, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        // Package-scoped, so only this app can receive it. The receiver is
        // declared in :app, which is where the kernel can actually be stopped
        // from — this module has no handle on the bridge.
        val disconnect = PendingIntent.getBroadcast(
            this,
            REQUEST_DISCONNECT,
            Intent(ACTION_DISCONNECT).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_running))
            // The system tints a small icon, so it has to be a silhouette. The
            // framework's dialog icon was a placeholder and looked like one.
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification),
                    getString(R.string.notification_disconnect),
                    disconnect,
                ).build(),
            )
            .build()
    }

    /** Reads a Go iterator into a callback; they are one-shot. */
    private inline fun StringIterator.forEach(action: (String) -> Unit) {
        while (hasNext()) action(next())
    }

    private inline fun RoutePrefixIterator.forEach(action: (io.nekohasekai.libbox.RoutePrefix) -> Unit) {
        while (hasNext()) action(next())
    }

    companion object {
        const val SESSION_NAME = "Starveil"
        const val CHANNEL_ID = "starveil.kernel"
        const val NOTIFICATION_CHANNEL_NAME = "Starveil"
        const val NOTIFICATION_ID = 1001

        /**
         * Sent by the notification's Disconnect action.
         *
         * A broadcast rather than a direct call because the code that can stop
         * the kernel lives in :app; this module only knows the action name.
         */
        const val ACTION_DISCONNECT = "com.justnpcx.starveil.action.DISCONNECT"

        private const val REQUEST_OPEN = 1
        private const val REQUEST_DISCONNECT = 2

        /** The running instance, so the platform interface can reach openTun. */
        @Volatile
        var current: StarveilVpnService? = null
            private set

        /** Null when consent is already granted; otherwise the consent intent. */
        fun consentIntent(context: android.content.Context): Intent? = prepare(context)

        /** Start (or no-op if already running) the foreground VPN service. */
        fun start(context: android.content.Context) {
            val intent = Intent(context, StarveilVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            KernelDiagnostics.log("VpnService stop requested")
            // Close the tun BEFORE asking the system to stop us. The interface
            // is what keeps the VPN framework bound to this service, and while
            // it is bound stopService does not reach onDestroy — device log:
            //   VpnService stop requested
            //   stop: kernel closed, stopping the VpnService
            //   (no onDestroy, and the VPN icon stayed)
            current?.closeTun()
            context.stopService(Intent(context, StarveilVpnService::class.java))
        }
    }
}
