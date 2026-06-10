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
    fun observeCheckedSince(sinceUtc: Long): Flow<List<OccurrenceRow>> =
        db.occurrences().observeCheckedSince(sinceUtc)
    fun observeOverrides(reminderLocalId: Long): Flow<List<ReminderOverrideRow>> =
        db.overrides().observeForReminder(reminderLocalId)
    fun observeOverridesFrom(localDate: String): Flow<List<ReminderOverrideRow>> =
        db.overrides().observeFrom(localDate)

    suspend fun createReminder(
        description: String,
        kind: ScheduleKind,
        dailyMinuteOfDay: Int?,
        oneTimeDueAtUtc: Long?,
        weeklyDaysMask: Int?,
        monthlyDayOfMonth: Int?,
    ): Long {
        val id = db.reminders().insert(ReminderRow(
            description = description,
            scheduleKind = kind,
            dailyMinuteOfDay = dailyMinuteOfDay,
            oneTimeDueAtUtc = oneTimeDueAtUtc,
            weeklyDaysMask = weeklyDaysMask,
            monthlyDayOfMonth = monthlyDayOfMonth,
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

    /** Reverse a check. If the row has not synced yet, drop it entirely. */
    suspend fun uncheckOccurrence(localId: Long) {
        val o = db.occurrences().findByLocalId(localId) ?: return
        if (o.checkedAtUtc == null) return
        if (o.serverId == null) {
            db.occurrences().deleteByLocalId(localId)
        } else {
            db.occurrences().update(o.copy(
                checkedAtUtc = null,
                pendingCheck = true,
            ))
            sync.triggerSync()
        }
    }

    /** Insert an occurrence already marked checked, for items the user checks before they fire. */
    suspend fun checkAhead(reminderLocalId: Long, dueAtUtc: Long): Long {
        val now = System.currentTimeMillis()
        val id = db.occurrences().insert(OccurrenceRow(
            reminderLocalId = reminderLocalId,
            dueAtUtc = dueAtUtc,
            checkedAtUtc = now,
            pendingCreate = true,
            pendingCheck = true,
        ))
        sync.triggerSync()
        return id
    }

    suspend fun findReminder(localId: Long): ReminderRow? = db.reminders().findByLocalId(localId)

    suspend fun uncheckedOccurrencesFor(reminderLocalId: Long): List<OccurrenceRow> =
        db.occurrences().uncheckedForReminder(reminderLocalId)

    /** Local ids of reminders checked off at or after [sinceUtc] (e.g. since local midnight). */
    suspend fun checkedReminderIdsSince(sinceUtc: Long): Set<Long> =
        db.occurrences().checkedSince(sinceUtc).map { it.reminderLocalId }.toSet()

    suspend fun findOverride(reminderLocalId: Long, localDate: String): ReminderOverrideRow? =
        db.overrides().findFor(reminderLocalId, localDate)

    suspend fun setOverride(reminderLocalId: Long, localDate: String, minuteOfDay: Int) {
        db.overrides().upsert(
            ReminderOverrideRow(
                reminderLocalId = reminderLocalId,
                localDate = localDate,
                minuteOfDay = minuteOfDay,
            )
        )
    }

    suspend fun deleteOverride(overrideId: Long) {
        db.overrides().deleteById(overrideId)
    }

    suspend fun deleteOverridesForReminder(reminderLocalId: Long) {
        db.overrides().deleteByReminder(reminderLocalId)
    }

    // --- Checklist (local-only; no backend sync) ---

    fun observeChecklistItems(): Flow<List<ChecklistItemRow>> = db.checklist().observeItems()

    fun observeChecklistChecksOn(localDate: String): Flow<List<ChecklistCheckRow>> =
        db.checklist().observeChecksOn(localDate)

    /** Replace a reminder's checklist template with [texts] (blanks dropped), preserving order. */
    suspend fun setChecklist(reminderLocalId: Long, texts: List<String>) {
        val dao = db.checklist()
        for (old in dao.itemsFor(reminderLocalId)) dao.deleteChecksForItem(old.id)
        dao.deleteItemsForReminder(reminderLocalId)
        texts.map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, text ->
            dao.insertItem(ChecklistItemRow(reminderLocalId = reminderLocalId, text = text, position = i))
        }
    }

    suspend fun deleteChecklistForReminder(reminderLocalId: Long) {
        val dao = db.checklist()
        for (old in dao.itemsFor(reminderLocalId)) dao.deleteChecksForItem(old.id)
        dao.deleteItemsForReminder(reminderLocalId)
    }

    suspend fun setChecklistCheck(itemId: Long, localDate: String, checked: Boolean) {
        val dao = db.checklist()
        if (checked) dao.insertCheck(ChecklistCheckRow(checklistItemLocalId = itemId, localDate = localDate))
        else dao.deleteCheck(itemId, localDate)
    }
}
