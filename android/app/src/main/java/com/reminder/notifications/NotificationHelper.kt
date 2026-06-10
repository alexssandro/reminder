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
            .setSmallIcon(R.drawable.ic_stat_calendar)
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

    /**
     * Pre-fire countdown notification. Reuses one ID per reminder so successive offsets
     * (1h → 30m → 10m) replace each other instead of stacking.
     */
    fun showPre(ctx: Context, reminderLocalId: Long, title: String, text: String) {
        ensureChannel(ctx)

        val contentIntent = PendingIntent.getActivity(
            ctx, preNotificationId(reminderLocalId),
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_calendar)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        nm.notify(preNotificationId(reminderLocalId), n)
    }

    fun cancelPre(ctx: Context, reminderLocalId: Long) {
        val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        nm.cancel(preNotificationId(reminderLocalId))
    }

    /** Negative-int namespace so it can't collide with positive occurrence-row IDs. */
    private fun preNotificationId(reminderLocalId: Long): Int = -(reminderLocalId.toInt() + 1)

    fun showDailyPreview(
        ctx: Context,
        items: List<TodayItem>,
        // Pre-formatted lines for no-time-of-day reminders, e.g. "Anytime  Water plants".
        untimedLines: List<String> = emptyList(),
    ) {
        ensureChannel(ctx)

        val contentIntent = PendingIntent.getActivity(
            ctx, PREVIEW_NOTIFICATION_ID,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val timedLines = items.map { "${it.timeLabel}  ${it.description}" }
        val body = (timedLines + untimedLines).joinToString("\n")
        val total = items.size + untimedLines.size
        val summary = ctx.resources.getQuantityString(
            R.plurals.preview_items_count, total, total,
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_PREVIEW_ID)
            .setSmallIcon(R.drawable.ic_stat_calendar)
            .setContentTitle(ctx.getString(R.string.preview_title))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            // Persistent: stays in the shade until the user dismisses it (tapping won't clear it).
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        nm.notify(PREVIEW_NOTIFICATION_ID, n)
    }
}
