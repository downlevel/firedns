package dev.downlevel.firedns.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.downlevel.firedns.MainActivity
import dev.downlevel.firedns.R

/** "Update available" and "Updated, see what's new": both open the app. */
object UpdateNotifications {
    private const val CHANNEL_ID = "updates"
    private const val ID_AVAILABLE = 2
    private const val ID_UPDATED = 3

    fun showAvailable(context: Context, versionName: String) = show(
        context,
        ID_AVAILABLE,
        context.getString(R.string.notif_update_available, versionName),
        context.getString(R.string.notif_update_available_text)
    )

    fun showUpdated(context: Context, versionName: String) {
        cancelAvailable(context)
        show(
            context,
            ID_UPDATED,
            context.getString(R.string.notif_updated, versionName),
            context.getString(R.string.notif_updated_text)
        )
    }

    fun cancelAvailable(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_AVAILABLE)
    }

    fun cancelUpdated(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_UPDATED)
    }

    private fun show(context: Context, id: Int, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}
