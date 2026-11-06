package com.justnpcx.starveil.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * The one real node the stability harnesses connect to — benchmark variant only.
 *
 * The scroll benchmark can invent a thousand rows because it only measures
 * layout. Chaos and soak cannot: they need a tunnel that actually carries
 * traffic, and a node that can be made to disappear. The node is a Shadowsocks
 * server on the lab host, passed in as extras so no address or password is ever
 * committed. This receiver only writes the row and the three preferences the
 * harnesses depend on; it never starts or stops the tunnel itself.
 *
 * Invoked with:
 * `am broadcast -n com.justnpcx.starveil/.benchmark.LabNodeReceiver \
 *    -a com.justnpcx.starveil.benchmark.LAB_NODE \
 *    --es host 192.0.2.10 --ei port 18388 \
 *    --es method aes-256-gcm --es password test --es name "Lab SS" \
 *    --es killSwitch block_on_disconnect`
 *
 * The address above is TEST-NET-1 on purpose: an example, never the lab host.
 */
class LabNodeReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LabEntryPoint {
        fun profiles(): ProfileRepository
        fun preferences(): PreferencesRepository
        fun database(): com.justnpcx.starveil.core.data.StarveilDatabase
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LAB_NODE) return
        val host = intent.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        val port = intent.getIntExtra(EXTRA_PORT, 0)
        val method = intent.getStringExtra(EXTRA_METHOD)?.trim().orEmpty()
        val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME)?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "Lab SS"
        if (host.isEmpty() || port !in 1..65535 || method.isEmpty() || password.isEmpty()) {
            android.util.Log.w(TAG, "LAB_NODE ignored: host/port/method/password incomplete")
            return
        }

        val killSwitch = when (intent.getStringExtra(EXTRA_KILL_SWITCH)?.lowercase()) {
            "always_block" -> KillSwitchMode.ALWAYS_BLOCK
            "block_on_disconnect" -> KillSwitchMode.BLOCK_ON_DISCONNECT
            else -> KillSwitchMode.OFF
        }

        val app = context.applicationContext
        val entry = EntryPointAccessors.fromApplication(app, LabEntryPoint::class.java)
        val marker = File(app.getExternalFilesDir(null), "lab-node.done")

        runBlocking {
            // Upsert rather than insert: re-running the harness must not grow a
            // second row with the same name, because the selection would then
            // point at whichever id the previous run happened to leave behind.
            val existing = entry.profiles().observeAll().first().firstOrNull { it.name == name }
            val profile = Profile(
                id = existing?.id ?: 0L,
                name = name,
                type = ProfileType.SHADOWSOCKS,
                server = host,
                port = port,
                settings = ShadowsocksSettings(method = method, password = password),
            )
            val id = entry.database().withTransaction {
                entry.profiles().upsert(profile)
            }
            entry.preferences().setSelectedProfileId(id)
            entry.preferences().setRoutePreset(RoutePreset.GLOBAL_PROXY)
            entry.preferences().setAutoReconnect(true)
            entry.preferences().setKillSwitchMode(killSwitch)

            marker.parentFile?.mkdirs()
            marker.writeText("id=$id\nname=$name\nkillSwitch=$killSwitch\n")
            android.util.Log.i(TAG, "LAB_NODE id=$id host=$host:$port killSwitch=$killSwitch")
        }
    }

    private companion object {
        const val TAG = "StarveilLab"
        const val ACTION_LAB_NODE = "com.justnpcx.starveil.benchmark.LAB_NODE"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_METHOD = "method"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_NAME = "name"
        const val EXTRA_KILL_SWITCH = "killSwitch"
    }
}
