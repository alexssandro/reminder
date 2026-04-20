package com.reminder.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object DailyPreviewScheduler {
    const val PREVIEW_HOUR = 8
    private const val REQUEST_CODE = 999001

    fun scheduleNext(ctx: Context, fromMillis: Long = System.currentTimeMillis()) {
        val triggerAt = nextPreviewAtUtc(fromMillis, PREVIEW_HOUR)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, REQUEST_CODE,
            Intent(ctx, DailyPreviewReceiver::class.java).apply {
                action = DailyPreviewReceiver.ACTION_PREVIEW
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    private fun pendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, DailyPreviewReceiver::class.java).apply {
            action = DailyPreviewReceiver.ACTION_PREVIEW
        }
        return PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
