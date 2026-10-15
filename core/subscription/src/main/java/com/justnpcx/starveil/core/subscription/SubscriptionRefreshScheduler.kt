package com.justnpcx.starveil.core.subscription

import android.content.Context

/**
 * Keeps a subscription group's periodic refresh tied to the group's lifetime:
 * scheduled when the group is imported, cancelled when it is deleted.
 *
 * This is a seam, not an abstraction for its own sake — it lets [LinkImporter]
 * and the feature layer stay plain JVM classes in tests. Only the production
 * implementation needs a [Context], and it is built by `SubscriptionModule`.
 */
interface SubscriptionRefreshScheduler {
    fun schedule(groupId: Long)
    fun cancel(groupId: Long)
}

class WorkManagerRefreshScheduler(private val context: Context) : SubscriptionRefreshScheduler {

    override fun schedule(groupId: Long) =
        SubscriptionUpdateScheduler.schedule(context, groupId)

    override fun cancel(groupId: Long) =
        SubscriptionUpdateScheduler.cancel(context, groupId)
}
