package com.justnpcx.starveil

import android.app.Application
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.widget.StarveilWidget
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Hilt graph root.
 *
 * [onCreate] stays free of heavy work: besides loading the native database
 * library, the only thing here is the widget's state feed, which runs on a
 * background scope and never blocks the first frame.
 */
@HiltAndroidApp
class StarveilApp : Application() {

    @Inject
    lateinit var kernel: KernelBridge

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // SQLCipher is a native library and must be loaded before the first
        // database access; the Room instance is built lazily on first injection.
        System.loadLibrary("sqlcipher")

        // A widget is RemoteViews rendered by the launcher — it cannot observe
        // anything, so the process that owns the state has to push. That works
        // because while the tunnel is up this process is alive by definition:
        // the VPN service runs in it.
        scope.launch {
            kernel.state.collect { StarveilWidget().updateAll(this@StarveilApp) }
        }
    }
}
