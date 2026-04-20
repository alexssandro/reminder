package com.reminder.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.reminder.MainActivity
import com.reminder.R

object NotificationHelper {
    const val CHANNEL_ID = "reminders"
    const val CHANNEL_PREVIEW_ID = "daily_preview"
    private const val PREVIEW_NOTIFICATION_ID = 999_001

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_HIGH,
            ))
        }
        if (nm.getNotificationChannel(CHANNEL_PREVIEW_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_PREVIEW_ID,
                ctx.getString(R.string.channel_daily_preview),
                NotificationManager.IMPORTANCE_DEFAULT,
            ))
        }
    }

    fun show(ctx: Context, occurrenceLocalId: Long, title: String, text: String) {
        ensureChannel(ctx)

        val contentIntent = PendingIntent.getActivity(
            ctx, occurrenceLocalId.toInt(),
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val doneIntent = PendingIntent.getBroadcast(
            ctx, occurrenceLocalId.toInt(),
            Intent(ctx, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_CHECK
                putExtra(ReminderActionReceiver.EXTRA_OCCURRENCE_LOCAL_ID, occurrenceLocalId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.checkbox_on_background,
                ctx.getString(R.string.action_done), doneIntent)
            .setAutoCancel(true)
            .build()

        val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        nm.notify(occurrenceLocalId.toInt(), n)
    }

    fun cancel(ctx: Context, occurrenceLocalId: Long) {
        val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        nm.cancel(occurrenceLocalId.toInt())
    }

    fun showDailyPreview(ctx: Context, items: List<TodayItem>) {
        ensureChannel(ctx)

        val contentIntent = PendingIntent.getActivity(
            ctx, PREVIEW_NOTIFICATION_ID,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val body = items.joinToString("\n") { "${it.timeLabel}  ${it.description}" }
        val summary = ctx.resources.getQuantityString(
            R.plurals.preview_items_count, items.size, items.size,
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_PREVIEW_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(ctx.getString(R.string.preview_title))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        nm.notify(PREVIEW_NOTIFICATION_ID, n)
    }
}
