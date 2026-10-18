package com.justnpcx.starveil.scene

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import androidx.core.content.ContextCompat
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.scene.SceneApplyState
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.core.model.NetworkState
import com.justnpcx.starveil.core.model.NetworkTransport
import com.justnpcx.starveil.core.model.Scene
import com.justnpcx.starveil.core.model.SceneMatcher
import com.justnpcx.starveil.core.model.SceneTarget
import com.justnpcx.starveil.core.model.SceneTiming
import com.justnpcx.starveil.feature.dashboard.ConnectionController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Applies scenes when the network changes.
 *
 * Watches the *default* network rather than every network: a scene is about
 * "where is traffic going right now", and a second interface coming up (a
 * tethering link, a VPN) is not a change of situation.
 *
 * Debounced by [SceneTiming.DEBOUNCE_MS]: a phone moving between cells fires
 * `onCapabilitiesChanged` several times in a row, and applying a scene on each
 * one would rebuild the tunnel repeatedly for a single real change.
 */
@Singleton
class SceneController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
    private val connection: ConnectionController,
    private val kernel: KernelBridge,
    private val applyState: SceneApplyState,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val debouncer = Debouncer(scope, SceneTiming.DEBOUNCE_MS)
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Registers the network callback. Idempotent. */
    fun start() {
        if (callback != null) return
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val registered = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = schedule()

            override fun onLost(network: Network) = schedule()

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                schedule()
        }
        runCatching { manager.registerDefaultNetworkCallback(registered) }
        callback = registered
    }

    private fun schedule() {
        // Latest wins: a network that flaps four times in five seconds produces
        // one evaluation, of the state it ended in.
        debouncer.submit { evaluate(System.currentTimeMillis()) }
    }

    private suspend fun currentTarget(): SceneTarget? = when (val target = preferences.connectionTarget.first()) {
        is com.justnpcx.starveil.core.data.preferences.ConnectionTarget.Node ->
            SceneTarget.Node(target.id)
        is com.justnpcx.starveil.core.data.preferences.ConnectionTarget.Group ->
            SceneTarget.Group(target.id)
        com.justnpcx.starveil.core.data.preferences.ConnectionTarget.None -> null
    }

    /** Whether the SSID can be read at all (location permission, Android 10+). */
    fun ssidReadable(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** The current default network, or null when there is none. */
    fun currentState(): NetworkState? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = manager.activeNetwork ?: return null
        val caps = manager.getNetworkCapabilities(network) ?: return null
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            else -> NetworkTransport.OTHER
        }
        val ssid = if (transport == NetworkTransport.WIFI) readSsid(caps) else null
        return NetworkState(transport = transport, ssid = ssid)
    }

    /**
     * The Wi-Fi name, or null when it cannot be read.
     *
     * Android 10 requires location permission for this, and returns
     * `<unknown ssid>` without it. That is not an error to log on every network
     * change — it is the degraded mode the UI explains.
     */
    private fun readSsid(caps: NetworkCapabilities): String? {
        if (!ssidReadable()) return null
        // Two sources, because they disagree. `transportInfo` is the modern one
        // and on this ROM it redacts the name even with permission granted
        // (observed: `<unknown ssid>` while WifiManager reports "Jim_5G").
        // WifiManager needs ACCESS_WIFI_STATE, which is why that permission is
        // declared; it names the network that is actually connected.
        val fromCaps = (caps.transportInfo as? WifiInfo)?.ssid?.trim('"')
        val fromManager = runCatching {
            context.getSystemService(android.net.wifi.WifiManager::class.java)
                ?.connectionInfo?.ssid?.trim('"')
        }.getOrNull()
        return sequenceOf(fromCaps, fromManager).firstOrNull { candidate ->
            !candidate.isNullOrBlank() && candidate != UNKNOWN_SSID
        }
    }

    private companion object {
        /** What Android returns instead of an SSID without permission. */
        const val UNKNOWN_SSID = "<unknown ssid>"
    }

    /**
     * Decide and apply, once. [nowMs] is a parameter so the timing rules can be
     * tested without waiting half an hour.
     */
    internal suspend fun evaluate(nowMs: Long) {
        val state = currentState() ?: return
        if (!SceneTiming.allowsAutomatic(nowMs, preferences.manualPresetAt.first())) {
            // The user chose a preset by hand recently; that outranks a scene.
            return
        }
        val scenes = preferences.scenes.first()
        if (scenes.isEmpty()) return
        val scene = SceneMatcher.match(scenes, state, ssidReadable()) ?: return
        apply(scene, state, nowMs)
    }

    private suspend fun apply(scene: Scene, state: NetworkState, nowMs: Long) {
        val presetChanged = preferences.routePreset.first() != scene.preset
        // A scene that is already in force has nothing to do. Without this the
        // callback at app start re-applies the same policy five seconds later
        // and rebuilds the tunnel a second time, while the first connect is
        // still settling — which is how a working connect turned into a failed
        // restart on device.
        val targetChanged = scene.target != null && scene.target != currentTarget()
        if (!presetChanged && !targetChanged) return

        preferences.setRoutePreset(scene.preset)
        scene.target?.let { target ->
            when (target) {
                is SceneTarget.Node -> preferences.setSelectedProfileId(target.profileId)
                is SceneTarget.Group -> preferences.setSelectedGroupId(target.groupId)
            }
        }

        // The dashboard may not be alive (the whole point of a scene is that it
        // works without anyone looking), so the rebuild is done here. The
        // watcher there skips its own when this timestamp is fresh.
        if (kernel.state.value == KernelState.Running) {
            connection.reapply()
        }
        applyState.lastAppliedAtMs = nowMs

        val where = state.ssid ?: when (state.transport) {
            NetworkTransport.CELLULAR -> "mobile data"
            NetworkTransport.WIFI -> "Wi-Fi"
            NetworkTransport.OTHER -> "network"
        }
        KernelDiagnostics.log("scene '${scene.name}' applied: ${scene.preset.name} ($where)")
        SceneNotifications.post(context, scene, where)
    }
}
