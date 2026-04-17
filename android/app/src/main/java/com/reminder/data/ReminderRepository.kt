package com.reminder.data

import android.content.Context
import com.reminder.sync.SyncManager
import kotlinx.coroutines.flow.Flow

/**
 * Local-first repository. All mutations write to Room and mark the row pending, then nudge
 * the SyncManager. Reads come from Room so the UI works fully offline.
 */
class ReminderRepository(ctx: Context) {
    private val db = AppDatabase.get(ctx)
    private val sync = SyncManager.get(ctx)

    fun observeReminders(): Flow<List<ReminderRow>> = db.reminders().observeAll()
    fun observePendingOccurrences(): Flow<List<OccurrenceRow>> = db.occurrences().observePending()

    suspend fun createReminder(
        description: String,
        kind: ScheduleKind,
        dailyMinuteOfDay: Int?,
        oneTimeDueAtUtc: Long?,
    ): Long {
        val id = db.reminders().insert(ReminderRow(
            description = description,
            scheduleKind = kind,
            dailyMinuteOfDay = dailyMinuteOfDay,
            oneTimeDueAtUtc = oneTimeDueAtUtc,
            pendingCreate = true,
        ))
        sync.triggerSync()
        return id
    }

    suspend fun updateReminder(r: ReminderRow) {
        db.reminders().update(r.copy(pendingUpdate = true, updatedAtLocal = System.currentTimeMillis()))
        sync.triggerSync()
    }

    suspend fun deleteReminder(localId: Long) {
        val r = db.reminders().findByLocalId(localId) ?: return
        if (r.serverId == null) {
            db.reminders().deleteByLocalId(localId)
        } else {
            db.reminders().update(r.copy(pendingDelete = true))
        }
        sync.triggerSync()
    }

    suspend fun activeReminders(): List<ReminderRow> = db.reminders().activeList()

    suspend fun recordFire(reminderLocalId: Long, dueAtUtc: Long): Long {
        val id = db.occurrences().insert(OccurrenceRow(
            reminderLocalId = reminderLocalId,
            dueAtUtc = dueAtUtc,
            pendingCreate = true,
        ))
        sync.triggerSync()
        return id
    }

    suspend fun checkOccurrence(localId: Long) {
        val o = db.occurrences().findByLocalId(localId) ?: return
        if (o.checkedAtUtc != null) return
        db.occurrences().update(o.copy(
            checkedAtUtc = System.currentTimeMillis(),
            pendingCheck = true,
        ))
        sync.triggerSync()
    }

    suspend fun findReminder(localId: Long): ReminderRow? = db.reminders().findByLocalId(localId)
}
