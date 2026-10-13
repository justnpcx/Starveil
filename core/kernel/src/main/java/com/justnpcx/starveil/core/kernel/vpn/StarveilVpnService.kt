package com.justnpcx.starveil.core.kernel.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
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
        startForeground(NOTIFICATION_ID, buildNotification())
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
     * @throws IllegalStateException when VPN consent has not been granted.
     */
    fun openTun(options: TunOptions): Int {
        closeTun()

        val builder = Builder()
            .setSession(SESSION_NAME)
            .setMtu(options.mtu)
            .setBlocking(true)

        if (options.autoRoute) {
            options.inet4RouteAddress?.forEach { builder.addRoute(it.address(), it.prefix()) }
            options.inet6RouteAddress?.forEach { builder.addRoute(it.address(), it.prefix()) }
        }
        options.inet4Address?.forEach { builder.addAddress(it.address(), it.prefix()) }
        options.inet6Address?.forEach { builder.addAddress(it.address(), it.prefix()) }

        options.dnsServerAddress?.forEach { builder.addDnsServer(it) }
        // dnsMode is advisory (sing-box resolves it); we do not set a resolver
        // from it because Builder has no equivalent.

        options.includePackage?.forEach { runCatching { builder.addAllowedApplication(it) } }
        options.excludePackage?.forEach { runCatching { builder.addDisallowedApplication(it) } }

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
