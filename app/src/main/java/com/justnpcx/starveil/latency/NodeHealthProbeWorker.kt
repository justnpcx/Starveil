package com.justnpcx.starveil.latency

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The background health sweep.
 *
 * WorkManager rather than a service or an alarm: the constraints are the point
 * (on a network, battery not low), and WorkManager is the component that knows
 * how to wait for them and to survive a reboot without a permission of its own.
 *
 * The sweep writes the time it ran so the app can tell "stale" from "fresh"
 * without reading the samples, which expire after thirty minutes and would make
 * every launch look stale.
 */
class NodeHealthProbeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            NodeHealthProbeEntryPoint::class.java,
        )
        val nowMs = System.currentTimeMillis()
        return try {
            val probed = entry.batchProbe().run(nowMs)
            entry.preferences().recordHealthProbe(nowMs)
            // One line in the Logs screen, because this is the only part of the
            // app that works while nobody is looking: without it there is no way
            // to tell "ran and found nothing due" from "never ran".
            KernelDiagnostics.log("health sweep: probed $probed node(s)")
            Result.success()
        } catch (e: Exception) {
            // Not fatal: the next sweep is two hours away and the picker keeps
            // working on what it already has. Retry so a transient failure does
            // not cost a whole interval.
            KernelDiagnostics.log("health sweep failed: ${e.javaClass.simpleName}")
            Result.retry()
        }
    }
}

/** Hilt bridge for the worker, which WorkManager instantiates reflectively. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NodeHealthProbeEntryPoint {
    fun batchProbe(): NodeHealthBatchProbe
    fun preferences(): PreferencesRepository
}
