package com.justnpcx.starveil.core.kernel.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
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

    private var tun: ParcelFileDescriptor? = null

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
        return START_STICKY
    }

    override fun onRevoke() {
        // The system revoked our VPN: tear down so the kernel does not keep
        // running against a dead tun.
        closeTun()
        current = null
        stopSelf()
    }

    override fun onDestroy() {
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
        closeTun()

        val builder = Builder()
            .setSession(SESSION_NAME)
            .setMtu(options.mtu)
            .setBlocking(true)

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

        // Diagnostic: what the kernel actually asked for. Without routes (or
        // without an address) the tun is established but captures nothing, and
        // the kernel's own traffic counter then reports zero forever.
        android.util.Log.i(
            "StarveilKernel",
            "openTun: mtu=${options.mtu} autoRoute=${options.autoRoute} " +
                "v4addr=$v4Addrs v4routes=$v4Routes v6addr=$v6Addrs v6routes=$v6Routes dns=$dnsServers",
        )

        val descriptor = builder.establish()
            ?: throw IllegalStateException("VPN consent not granted")

        tun = descriptor
        return descriptor.detachFd()
    }

    private fun closeTun() {
        runCatching { tun?.close() }
        tun = null
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
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(NOTIFICATION_TITLE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
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
        const val NOTIFICATION_TITLE = "Starveil is running"
        const val NOTIFICATION_ID = 1001

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
            context.stopService(Intent(context, StarveilVpnService::class.java))
        }
    }
}
