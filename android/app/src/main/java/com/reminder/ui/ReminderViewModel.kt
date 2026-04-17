package com.reminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.data.OccurrenceRow
import com.reminder.data.ReminderRepository
import com.reminder.data.ReminderRow
import com.reminder.data.ScheduleKind
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

    fun createReminder(
        description: String,
        kind: ScheduleKind,
        dailyMinuteOfDay: Int?,
        oneTimeDueAtUtc: Long?,
    ) = viewModelScope.launch {
        val localId = repo.createReminder(description, kind, dailyMinuteOfDay, oneTimeDueAtUtc)
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
        repo.deleteReminder(r.id)
    }

    fun check(occurrence: OccurrenceRow) = viewModelScope.launch {
        repo.checkOccurrence(occurrence.id)
        ReminderScheduler.cancelRepeat(getApplication(), occurrence.id)
        NotificationHelper.cancel(getApplication(), occurrence.id)
    }
}
