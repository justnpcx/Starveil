package com.justnpcx.starveil.scene

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.model.Scene

/**
 * The "a scene switched you" notification.
 *
 * Low importance and auto-cancel: this is a record, not an alarm. A scene that
 * fires every time the phone changes network would be intolerable at default
 * importance, and the log line is there for anyone who wants the history.
 *
 * Notification permission is not required to call this — without it the system
 * drops the notification, which is fine: the switch still happened and the log
 * still says so.
 */
internal object SceneNotifications {

    private const val CHANNEL_ID = "starveil.scenes"
    private const val NOTIFICATION_ID = 1002

    fun post(context: Context, scene: Scene, where: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.scene_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.scene_channel_desc)
                setShowBadge(false)
            },
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scene)
            .setContentTitle(context.getString(R.string.scene_applied_title))
            .setContentText(context.getString(R.string.scene_applied_body, scene.name, where))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
