package com.reminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminder.data.ReminderRepository
import com.reminder.data.ScheduleKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles both the primary fire (ACTION_FIRE) and the hourly re-reminder tick (ACTION_REPEAT).
 * Each path: create-or-reuse a local Occurrence, show a notification, then re-arm either
 * the next daily schedule (primary) or the next +1h repeat (repeat).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val repo = ReminderRepository(ctx)
                val reminderLocalId = intent.getLongExtra(EXTRA_REMINDER_LOCAL_ID, -1L)
                val dueAtUtc = intent.getLongExtra(EXTRA_DUE_AT_UTC, System.currentTimeMillis())
                val reminder = repo.findReminder(reminderLocalId) ?: return@launch
                if (!reminder.isActive) return@launch

                val occurrenceLocalId: Long = when (intent.action) {
                    ACTION_FIRE -> repo.recordFire(reminderLocalId, dueAtUtc)
                    ACTION_REPEAT -> intent.getLongExtra(EXTRA_OCCURRENCE_LOCAL_ID, -1L)
                        .takeIf { it > 0 } ?: return@launch
                    else -> return@launch
                }

                NotificationHelper.show(
                    ctx,
                    occurrenceLocalId = occurrenceLocalId,
                    title = reminder.description,
                    text = "Tap to open or mark done.",
                )

                // Re-ring every hour until the user checks.
                ReminderScheduler.scheduleRepeatReminder(
                    ctx, reminderLocalId, occurrenceLocalId,
                    System.currentTimeMillis() + HOUR_MILLIS,
                )

                // Primary fire on daily reminders: schedule tomorrow's slot.
                if (intent.action == ACTION_FIRE && reminder.scheduleKind == ScheduleKind.Daily) {
                    ReminderScheduler.scheduleNext(ctx, reminder)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.reminder.FIRE"
        const val ACTION_REPEAT = "com.reminder.REPEAT"
        const val EXTRA_REMINDER_LOCAL_ID = "reminderLocalId"
        const val EXTRA_OCCURRENCE_LOCAL_ID = "occurrenceLocalId"
        const val EXTRA_DUE_AT_UTC = "dueAtUtc"
        private const val HOUR_MILLIS = 60L * 60L * 1000L
    }
}
