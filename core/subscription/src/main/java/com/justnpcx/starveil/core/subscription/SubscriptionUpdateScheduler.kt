package com.justnpcx.starveil.core.subscription

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic subscription refresh.
 *
 * 6h interval with a 10-minute flex window, and it only runs when the device
 * has a network — a refresh with no connectivity would just burn a retry.
 */
object SubscriptionUpdateScheduler {

    const val WORK_NAME = "starveil-subscription-update"
    const val INTERVAL_HOURS = 6L
    const val FLEX_MINUTES = 10L

    /**
     * Unique work is per GROUP, not per feature. With one shared name the
     * second subscription's `schedule` would replace the first's request and
     * the first group would silently stop refreshing.
     */
    fun workName(groupId: Long): String = "$WORK_NAME-$groupId"

    fun periodicRequest(groupId: Long): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
            INTERVAL_HOURS,
            TimeUnit.HOURS,
            FLEX_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .setInputData(workDataOf(SubscriptionUpdateWorker.KEY_GROUP_ID to groupId))
            .build()

    /** Enqueue (or replace) the refresh for [groupId]. Idempotent. */
    fun schedule(context: Context, groupId: Long) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(groupId),
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest(groupId),
        )
    }

    /** Stop refreshing [groupId] — its group is gone, so the work is orphaned. */
    fun cancel(context: Context, groupId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(groupId))
    }
}
