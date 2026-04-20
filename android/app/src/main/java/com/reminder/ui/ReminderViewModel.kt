package com.reminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.data.OccurrenceRow
import com.reminder.data.ReminderOverrideRow
import com.reminder.data.ReminderRepository
import com.reminder.data.ReminderRow
import com.reminder.data.ScheduleKind
import kotlinx.coroutines.flow.Flow
import com.reminder.notifications.NotificationHelper
import com.reminder.notifications.ReminderScheduler
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
    ) = viewModelScope.launch {
        val localId = repo.createReminder(description, kind, dailyMinuteOfDay, oneTimeDueAtUtc, weeklyDaysMask)
        val r = repo.findReminder(localId) ?: return@launch
        ReminderScheduler.scheduleNext(getApplication(), r)
    }

    fun toggleActive(r: ReminderRow) = viewModelScope.launch {
        val updated = r.copy(isActive = !r.isActive)
        repo.updateReminder(updated)
        if (updated.isActive) ReminderScheduler.scheduleNext(getApplication(), updated)
        else ReminderScheduler.cancel(getApplication(), updated.id)
    }

    fun delete(r: ReminderRow) = viewModelScope.launch {
        ReminderScheduler.cancel(getApplication(), r.id)
        repo.deleteOverridesForReminder(r.id)
        repo.deleteReminder(r.id)
    }

    fun check(occurrence: OccurrenceRow) = viewModelScope.launch {
        repo.checkOccurrence(occurrence.id)
        ReminderScheduler.cancelRepeat(getApplication(), occurrence.id)
        NotificationHelper.cancel(getApplication(), occurrence.id)
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
