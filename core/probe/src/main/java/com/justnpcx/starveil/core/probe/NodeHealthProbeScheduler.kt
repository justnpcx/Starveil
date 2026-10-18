package com.justnpcx.starveil.core.probe

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the background health sweep scheduled.
 *
 * Two requests, not one: a periodic sweep every [INTERVAL_HOURS] hours, and a
 * one-off catch-up when the stored timestamp says the data is older than that.
 * The second is what makes the feature visible on a fresh install — otherwise
 * the first reading is two hours away and "automatic selection" still has
 * nothing to choose from.
 *
 * [ExistingPeriodicWorkPolicy.UPDATE] rather than KEEP: the interval and the
 * constraints are code, and an app update that changes them must not leave the
 * old request running forever.
 */
@Singleton
class NodeHealthProbeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
) {

    /**
     * Called once per process start. Cheap: enqueueing is a database write in
     * WorkManager's own store, and the catch-up decision is one preference read.
     */
    fun schedule(scope: CoroutineScope) {
        scope.launch {
            val workManager = WorkManager.getInstance(context)
            // Whether this is the first schedule, asked *before* enqueueing.
            // WorkManager runs a new periodic request immediately, so a
            // catch-up on that same launch would probe every node twice — which
            // is exactly what the first device run did.
            val alreadyScheduled = runCatching {
                workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_NAME).first()
            }.getOrNull().orEmpty().isNotEmpty()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<NodeHealthProbeWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                    .setConstraints(constraints())
                    .build(),
            )

            if (!alreadyScheduled) return@launch
            val last = preferences.lastHealthProbeAt.first()
            if (System.currentTimeMillis() - last < INTERVAL_MS) return@launch
            workManager.enqueueUniqueWork(
                CATCH_UP_NAME,
                // KEEP: a second launch while the first catch-up is still
                // waiting for a network must not enqueue another.
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<NodeHealthProbeWorker>()
                    .setConstraints(constraints())
                    .build(),
            )
        }
    }

    private fun constraints(): Constraints = Constraints.Builder()
        // CONNECTED, not UNMETERED: the sweep is a few kilobytes, and a phone on
        // mobile data is exactly when a user wants the node list to be current.
        .setRequiredNetworkType(NetworkType.CONNECTED)
        // The sweep is the only thing in this app that wakes the radio on its
        // own. Under 15% it waits; nothing is lost, the next sweep catches up.
        .setRequiresBatteryNotLow(true)
        .build()

    companion object {
        /** The task book's floor: never more often than this. */
        const val INTERVAL_HOURS = 2L

        val INTERVAL_MS = TimeUnit.HOURS.toMillis(INTERVAL_HOURS)

        const val PERIODIC_NAME = "node-health-probe"
        const val CATCH_UP_NAME = "node-health-probe-catch-up"
    }
}
