package com.reminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminder.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the "Mark done" action from a notification. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK) return
        val occurrenceLocalId = intent.getLongExtra(EXTRA_OCCURRENCE_LOCAL_ID, -1L)
        if (occurrenceLocalId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderRepository(ctx).checkOccurrence(occurrenceLocalId)
                ReminderScheduler.cancelRepeat(ctx, occurrenceLocalId)
                NotificationHelper.cancel(ctx, occurrenceLocalId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "com.reminder.CHECK"
        const val EXTRA_OCCURRENCE_LOCAL_ID = "occurrenceLocalId"
    }
}
