package com.reminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminder.data.AppDatabase
import com.reminder.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class DailyPreviewReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_PREVIEW) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now().toString()
                val db = AppDatabase.get(ctx)
                val reminders = ReminderRepository(ctx).activeReminders()
                val overrides = reminders.mapNotNull { r -> db.overrides().findFor(r.id, today) }
                val items = todayItems(reminders, System.currentTimeMillis(), overrides)
                if (items.isNotEmpty()) {
                    NotificationHelper.showDailyPreview(ctx, items)
                }
            } finally {
                // Chain tomorrow's preview regardless of whether we showed one today.
                DailyPreviewScheduler.scheduleNext(ctx)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PREVIEW = "com.reminder.DAILY_PREVIEW"
    }
}
