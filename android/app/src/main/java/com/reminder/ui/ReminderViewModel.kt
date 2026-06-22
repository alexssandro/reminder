package com.reminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.data.ChecklistCheckRow
import com.reminder.data.ChecklistItemRow
import com.reminder.data.OccurrenceRow
import com.reminder.data.ReminderOverrideRow
import com.reminder.data.ReminderRepository
import com.reminder.data.ReminderRow
import com.reminder.data.ScheduleKind
import com.reminder.data.WishlistItemRow
import kotlinx.coroutines.flow.Flow
import com.reminder.notifications.NotificationHelper
import com.reminder.notifications.ReminderScheduler
import com.reminder.notifications.reminderFiresAt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ReminderRepository(app)

    val reminders: StateFlow<List<ReminderRow>> =
        repo.observeReminders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pendingOccurrences: StateFlow<List<OccurrenceRow>> =
        repo.observePendingOccurrences().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val checkedToday: StateFlow<List<OccurrenceRow>> =
        repo.observeCheckedSince(startOfTodayUtcMillis())
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Reminders ever checked off — Anytime items use this for their "done forever" filter. */
    val everCheckedReminderIds: StateFlow<List<Long>> =
        repo.observeEverCheckedReminderIds()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val checklistItems: StateFlow<List<ChecklistItemRow>> =
        repo.observeChecklistItems().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val checklistChecksToday: StateFlow<List<ChecklistCheckRow>> =
        repo.observeChecklistChecksOn(java.time.LocalDate.now().toString())
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val wishlist: StateFlow<List<WishlistItemRow>> =
        repo.observeWishlist().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addWishlistItem(name: String, bestPrice: Double?, store: String?) = viewModelScope.launch {
        repo.addWishlistItem(name, bestPrice, store)
    }

    fun updateWishlistItem(item: WishlistItemRow) = viewModelScope.launch {
        repo.updateWishlistItem(item)
    }

    fun deleteWishlistItem(id: Long) = viewModelScope.launch {
        repo.deleteWishlistItem(id)
    }

    fun reorderWishlist(orderedIds: List<Long>) = viewModelScope.launch {
        repo.reorderWishlist(orderedIds)
    }

    private fun startOfTodayUtcMillis(): Long =
        java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    fun createReminder(
        description: String,
        kind: ScheduleKind,
        dailyMinuteOfDay: Int?,
        oneTimeDueAtUtc: Long?,
        weeklyDaysMask: Int?,
        monthlyDayOfMonth: Int?,
        checklist: List<String> = emptyList(),
    ) = viewModelScope.launch {
        val localId = repo.createReminder(
            description, kind, dailyMinuteOfDay, oneTimeDueAtUtc, weeklyDaysMask, monthlyDayOfMonth,
        )
        repo.setChecklist(localId, checklist)
        val r = repo.findReminder(localId) ?: return@launch
        ReminderScheduler.scheduleNext(getApplication(), r)
    }

    fun toggleActive(r: ReminderRow) = viewModelScope.launch {
        val updated = r.copy(isActive = !r.isActive)
        repo.updateReminder(updated)
        ReminderScheduler.cancel(getApplication(), updated.id)
        clearStaleAlarms(updated)
        if (updated.isActive) ReminderScheduler.scheduleNext(getApplication(), updated)
    }

    fun updateSchedule(
        r: ReminderRow,
        description: String,
        kind: ScheduleKind,
        dailyMinuteOfDay: Int?,
        oneTimeDueAtUtc: Long?,
        weeklyDaysMask: Int?,
        monthlyDayOfMonth: Int?,
        checklist: List<String> = emptyList(),
    ) = viewModelScope.launch {
        val updated = r.copy(
            description = description,
            scheduleKind = kind,
            dailyMinuteOfDay = dailyMinuteOfDay,
            oneTimeDueAtUtc = oneTimeDueAtUtc,
            weeklyDaysMask = weeklyDaysMask,
            monthlyDayOfMonth = monthlyDayOfMonth,
        )
        repo.updateReminder(updated)
        repo.setChecklist(updated.id, checklist)
        ReminderScheduler.cancel(getApplication(), updated.id)
        clearStaleAlarms(updated)
        if (updated.isActive) ReminderScheduler.scheduleNext(getApplication(), updated)
    }

    /**
     * After an edit / toggle, any unchecked occurrence whose dueAtUtc no longer matches the
     * reminder's current schedule is stale: stop its hourly re-ring and clear its notification
     * so the home screen and notification shade reflect the new schedule immediately.
     */
    private suspend fun clearStaleAlarms(r: ReminderRow) {
        val stale = repo.uncheckedOccurrencesFor(r.id)
            .filterNot { occ -> reminderFiresAt(r, occ.dueAtUtc) }
        for (occ in stale) {
            ReminderScheduler.cancelRepeat(getApplication(), occ.id)
            NotificationHelper.cancel(getApplication(), occ.id)
        }
    }

    fun delete(r: ReminderRow) = viewModelScope.launch {
        ReminderScheduler.cancel(getApplication(), r.id)
        repo.deleteOverridesForReminder(r.id)
        repo.deleteChecklistForReminder(r.id)
        repo.deleteReminder(r.id)
    }

    /** Tick or un-tick a checklist sub-item for today (resets each day). */
    fun toggleChecklistItem(itemId: Long, checked: Boolean) = viewModelScope.launch {
        repo.setChecklistCheck(itemId, java.time.LocalDate.now().toString(), checked)
    }

    fun check(occurrence: OccurrenceRow) = viewModelScope.launch {
        repo.checkOccurrence(occurrence.id)
        ReminderScheduler.cancelRepeat(getApplication(), occurrence.id)
        NotificationHelper.cancel(getApplication(), occurrence.id)
    }

    /** Check off a reminder before its scheduled time fires today. */
    fun checkAhead(reminder: ReminderRow, dueAtUtc: Long) = viewModelScope.launch {
        repo.checkAhead(reminder.id, dueAtUtc)
        ReminderScheduler.scheduleNextAfter(getApplication(), reminder, dueAtUtc + 1)
    }

    fun uncheck(occurrence: OccurrenceRow) = viewModelScope.launch {
        repo.uncheckOccurrence(occurrence.id)
    }

    fun observeOverrides(reminderLocalId: Long): Flow<List<ReminderOverrideRow>> =
        repo.observeOverrides(reminderLocalId)

    val overridesToday: StateFlow<List<ReminderOverrideRow>> =
        repo.observeOverridesFrom(java.time.LocalDate.now().toString())
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setOverride(r: ReminderRow, localDate: String, minuteOfDay: Int) = viewModelScope.launch {
        repo.setOverride(r.id, localDate, minuteOfDay)
        if (r.isActive) ReminderScheduler.scheduleNext(getApplication(), r)
    }

    fun removeOverride(r: ReminderRow, overrideId: Long) = viewModelScope.launch {
        repo.deleteOverride(overrideId)
        if (r.isActive) ReminderScheduler.scheduleNext(getApplication(), r)
    }
}
