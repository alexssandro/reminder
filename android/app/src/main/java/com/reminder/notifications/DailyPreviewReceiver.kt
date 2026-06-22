package com.reminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminder.data.AppDatabase
import com.reminder.data.ReminderRepository
import com.reminder.data.ScheduleKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class DailyPreviewReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_PREVIEW) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                buildAndShow(ctx)
            } finally {
                // Chain the next slot regardless of whether we showed one this time.
                DailyPreviewScheduler.scheduleNext(ctx)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PREVIEW = "com.reminder.DAILY_PREVIEW"

        /**
         * Gather today's reminders (timed + Anytime) and post the preview notification.
         * Shared by the scheduled fire and the debug test hooks. Returns true if a
         * notification was shown, false if everything was already checked off.
         */
        suspend fun buildAndShow(ctx: Context): Boolean {
            val today = LocalDate.now().toString()
            val db = AppDatabase.get(ctx)
            val repo = ReminderRepository(ctx)
            val reminders = repo.activeReminders()
            val overrides = reminders.mapNotNull { r -> db.overrides().findFor(r.id, today) }

            // Ignore anything already checked off today — the preview only nudges about what's
            // still outstanding. If that leaves nothing, we don't fire a notification at all.
            val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val checkedToday = repo.checkedReminderIdsSince(startOfToday)
            // Anytime is "done forever" once checked, so it's gated on ever-checked, not today's.
            val everChecked = repo.everCheckedReminderIds()

            val items = todayItems(reminders, System.currentTimeMillis(), overrides)
                .filter { it.reminderLocalId !in checkedToday }
            // Anytime and Monthly reminders have no time-of-day, so they aren't in todayItems;
            // list them separately so the daily preview still surfaces them to check off.
            val todayDate = LocalDate.now()
            val anytime = reminders
                .filter { it.scheduleKind == ScheduleKind.Anytime && it.id !in everChecked }
                .map { "Anytime  ${it.description}" }
            val monthly = reminders
                .filter {
                    it.scheduleKind == ScheduleKind.Monthly && it.id !in checkedToday &&
                        it.monthlyDayOfMonth?.let { d -> monthlyAvailableOn(todayDate, d) } == true
                }
                .map { "Monthly  ${it.description}" }
            val untimed = anytime + monthly
            if (items.isEmpty() && untimed.isEmpty()) return false
            NotificationHelper.showDailyPreview(ctx, items, untimed)
            return true
        }
    }
}
