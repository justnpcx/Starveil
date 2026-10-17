package com.justnpcx.starveil.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.feature.dashboard.ConnectionController
import com.justnpcx.starveil.feature.dashboard.ConnectionOutcome
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The quick-settings tile: connect or disconnect without opening the app.
 *
 * It can do what the notification cannot, because it exists while the tunnel is
 * down. Connect goes to whatever is already selected on the Nodes tab — a tile
 * has nowhere to put a node picker — and when nothing is selected it opens the
 * app instead of failing silently, because "nothing happened" is the worst
 * possible answer to a tap.
 *
 * Both directions go through [ConnectionController], the same code the
 * dashboard uses. A tile with its own copy of the connect path would be a
 * second implementation of the Kill Switch and the per-app rules, and the two
 * would drift the first time either changed.
 */
class StarveilTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var stateJob: Job? = null

    private val entry: TileEntryPoint
        get() = EntryPointAccessors.fromApplication(
            applicationContext,
            TileEntryPoint::class.java,
        )

    override fun onStartListening() {
        stateJob?.cancel()
        stateJob = scope.launch {
            entry.kernelBridge().state.collect { state -> updateTile(state) }
        }
    }

    override fun onStopListening() {
        stateJob?.cancel()
        stateJob = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        scope.launch {
            val controller = entry.connectionController()
            val kernel = entry.kernelBridge()
            val outcome = if (kernel.state.value == KernelState.Running) {
                controller.disconnect()
            } else {
                controller.connect()
            }

            updateTile(kernel.state.value)

            // Anything that did not come up needs a decision this tile cannot
            // make: no target chosen, an unbuildable config, a refusal worth
            // reading. Opening the app is the honest answer; staying silent is
            // not.
            when (outcome) {
                is ConnectionOutcome.NoTarget,
                is ConnectionOutcome.ConfigUnbuildable,
                is ConnectionOutcome.Refused,
                -> openApp()
                else -> Unit
            }
        }
    }

    private fun updateTile(state: KernelState) {
        val tile = qsTile ?: return
        val running = state == KernelState.Running
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(
            if (running) R.string.tile_connected else R.string.tile_disconnected,
        )
        tile.updateTile()
    }

    /**
     * The `Intent` overload is deprecated in 34 and the `PendingIntent` one does
     * not exist before it, so both are needed and lint has to be told the
     * version check is the point.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34 removed the Intent overload; the PendingIntent one also
            // tells the system what to do if the app has to start first.
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** Hilt bridge: a TileService is not an injection target here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TileEntryPoint {
    fun kernelBridge(): KernelBridge
    fun connectionController(): ConnectionController
}
