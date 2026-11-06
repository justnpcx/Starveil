package com.justnpcx.starveil.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelState
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
import java.io.File

/**
 * The kernel's own clock, written down — benchmark variant only.
 *
 * A UI poll tells the harness what the screen said when it happened to look;
 * it cannot tell the difference between "the kernel came back 300 ms ago" and
 * "it came back between two polls". Chaos needs the transition time, and the
 * only place that time exists is inside the process that owns the state flow.
 *
 * The receiver subscribes to [KernelBridge.state] and [KernelBridge.blocking]
 * and appends one line per transition, with [SystemClock.elapsedRealtime] so
 * the numbers are comparable with the test process's own clock on the same
 * device. It also accepts MARK broadcasts: the test brackets a drop with
 * `round_start` and `wifi_enable`, and the marks land in the same file as the
 * transitions.
 *
 * It writes to the app's external files directory, which the shell (and so
 * `adb pull`) can read without root. Nothing here is in the release variant.
 */
class ChaosStateReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ChaosEntryPoint {
        fun kernel(): KernelBridge
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_START -> start(app)
            ACTION_MARK -> {
                val label = intent.getStringExtra(EXTRA_LABEL) ?: return
                append(app, "${now()},MARK,$label")
                android.util.Log.i(TAG, "mark=$label elapsed=${now()}")
            }
            ACTION_STOP -> {
                append(app, "${now()},MARK,stop")
                job?.cancel()
                job = null
            }
        }
    }

    private fun start(app: Context) {
        job?.cancel()
        val entry = EntryPointAccessors.fromApplication(app, ChaosEntryPoint::class.java)
        val kernel = entry.kernel()
        val file = stateFile(app)
        file.parentFile?.mkdirs()
        synchronized(lock) {
            file.writeText("elapsedMs,event,detail\n")
        }
        job = scope.launch {
            launch {
                var previous: String? = null
                kernel.state.collect { state ->
                    // R8 renames data objects, so `::class.simpleName` is "p"
                    // in the benchmark variant. The harness needs the name the
                    // report uses, not the one the shrinker left behind.
                    val name = when (state) {
                        KernelState.Stopped -> "Stopped"
                        KernelState.Starting -> "Starting"
                        KernelState.Running -> "Running"
                        KernelState.Stopping -> "Stopping"
                        is KernelState.Error -> "Error"
                    }
                    if (name != previous) {
                        previous = name
                        append(app, "${now()},KERNEL,$name")
                        android.util.Log.i(TAG, "kernel=$name elapsed=${now()}")
                    }
                }
            }
            launch {
                kernel.blocking.collect { blocking ->
                    append(app, "${now()},BLOCKING,$blocking")
                    android.util.Log.i(TAG, "blocking=$blocking elapsed=${now()}")
                }
            }
        }
        android.util.Log.i(TAG, "state log started: ${file.absolutePath}")
    }

    private fun append(app: Context, line: String) {
        synchronized(lock) {
            runCatching { stateFile(app).appendText(line + "\n") }
        }
    }

    private fun stateFile(app: Context): File =
        File(File(app.getExternalFilesDir(null), "chaos"), "kernel-state.csv")

    private fun now(): Long = SystemClock.elapsedRealtime()

    private companion object {
        const val TAG = "StarveilChaos"
        const val ACTION_START = "com.justnpcx.starveil.benchmark.CHAOS_START"
        const val ACTION_MARK = "com.justnpcx.starveil.benchmark.CHAOS_MARK"
        const val ACTION_STOP = "com.justnpcx.starveil.benchmark.CHAOS_STOP"
        const val EXTRA_LABEL = "label"

        val lock = Any()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        var job: Job? = null
    }
}
