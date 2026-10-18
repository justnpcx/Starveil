package com.justnpcx.starveil.feature.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Where the keep-alive switches live on this phone.
 *
 * Every vendor hides the autostart toggle somewhere different, and the names
 * change between ROM versions. So this is a *list of candidates per vendor*,
 * resolved at runtime: the first one the system actually has wins, and when
 * none of them do the app-details page opens instead — which always exists and
 * always has the battery switch on it.
 *
 * Only the vivo entries were verified against a device (OriginOS, Android 17).
 * The others are the components the ROMs have shipped for years; a wrong guess
 * costs a fallback, not a crash.
 */
internal object KeepAliveTargets {

    /** Whether the system exempts this app from doze. */
    fun isExemptFromBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * The system dialog that offers the exemption.
     *
     * Falls back to the battery-optimisation list when the direct request is
     * not handled — some ROMs replace the dialog but keep the list.
     */
    fun batteryOptimizationIntent(context: Context): Intent {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        if (direct.resolveActivity(context.packageManager) != null) return direct
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    /**
     * The vendor's autostart manager, or the app's own details page.
     *
     * The fallback is not a failure message: on a ROM with no such manager the
     * app-details page is where the battery switch lives, and it is the page a
     * support answer would point at anyway.
     */
    fun autostartIntent(context: Context): Intent {
        val candidates = candidatesFor(manufacturer())
        for ((pkg, cls) in candidates) {
            val intent = Intent().setComponent(ComponentName(pkg, cls))
            if (intent.resolveActivity(context.packageManager) != null) return intent
        }
        return appDetailsIntent(context)
    }

    /** The app's own settings page — always available, always relevant. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))

    /** Whether the vendor page for this phone was found. */
    fun hasVendorAutostartPage(context: Context): Boolean {
        val candidates = candidatesFor(manufacturer())
        return candidates.any { (pkg, cls) ->
            Intent().setComponent(ComponentName(pkg, cls))
                .resolveActivity(context.packageManager) != null
        }
    }

    internal fun candidatesFor(manufacturer: String): List<Pair<String, String>> =
        when (manufacturer.lowercase()) {
            "vivo", "iqoo" -> listOf(
                // Verified on OriginOS / Android 17: opens 自启动.
                "com.vivo.permissionmanager" to
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            )

            "xiaomi", "redmi", "poco" -> listOf(
                "com.miui.securitycenter" to
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )

            "huawei", "honor" -> listOf(
                "com.huawei.systemmanager" to
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
            )

            "oppo", "realme", "oneplus" -> listOf(
                "com.coloros.safecenter" to
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
                "com.coloros.oppoguardelf" to
                    "com.coloros.oppoguardelf.powermgr.powersaver.PowerSaverActivity",
            )

            else -> emptyList()
        }

    private fun manufacturer(): String = android.os.Build.MANUFACTURER.orEmpty()

    /**
     * Starts an activity, falling back to the app details page.
     *
     * A vendor activity can be declared and still refuse to start (permission
     * guard, disabled component), so the resolution check is not enough — the
     * catch is what makes this honest.
     */
    fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: SecurityException) {
            context.startActivity(appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
