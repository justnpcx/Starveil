package com.justnpcx.starveil.core.subscription

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.justnpcx.starveil.core.data.repository.GroupRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Periodic subscription refresh.
 *
 * Only the group id travels in the work input; the URL is read from the group
 * itself, so a credential-bearing URL is not duplicated into WorkManager's
 * database.
 *
 * Failure -> [Result.retry]: WorkManager re-runs with exponential backoff, and
 * [SubscriptionUpdater] has already preserved the last-good profiles.
 */
class SubscriptionUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val groupId = inputData.getLong(KEY_GROUP_ID, -1L)
        if (groupId < 0L) return Result.failure()

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SubscriptionWorkerEntryPoint::class.java,
        )
        val url = entryPoint.groupRepository().getById(groupId)?.subscriptionUrl
        // No subscription on this group: nothing to do, and nothing to retry.
        if (url.isNullOrBlank()) return Result.success()

        return when (entryPoint.subscriptionUpdater().update(groupId, url)) {
            is SubscriptionUpdateOutcome.Success -> Result.success()
            is SubscriptionUpdateOutcome.Failure -> Result.retry()
        }
    }

    companion object {
        const val KEY_GROUP_ID = "groupId"
    }
}

/** Hilt bridge for the worker, which WorkManager instantiates reflectively. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SubscriptionWorkerEntryPoint {
    fun subscriptionUpdater(): SubscriptionUpdater
    fun groupRepository(): GroupRepository
}
