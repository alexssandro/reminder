package com.reminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminder.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-schedule all active reminders after the device reboots or the app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ReminderRepository(ctx)
                for (r in repo.activeReminders()) {
                    ReminderScheduler.scheduleNext(ctx, r)
                }
                DailyPreviewScheduler.scheduleNext(ctx)
            } finally {
                pending.finish()
            }
        }
    }
}
