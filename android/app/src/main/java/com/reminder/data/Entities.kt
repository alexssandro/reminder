package com.reminder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ScheduleKind(val api: Int) { Daily(1), OneTime(2), Weekly(3) }

@Entity(tableName = "reminders")
data class ReminderRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Int? = null,
    val description: String,
    val scheduleKind: ScheduleKind,
    val dailyMinuteOfDay: Int? = null,
    val oneTimeDueAtUtc: Long? = null,
    val weeklyDaysMask: Int? = null,
    val isActive: Boolean = true,
    val createdAtUtc: Long = System.currentTimeMillis(),
    val updatedAtLocal: Long = System.currentTimeMillis(),
    val pendingCreate: Boolean = true,
    val pendingUpdate: Boolean = false,
    val pendingDelete: Boolean = false,
)

@Entity(
    tableName = "reminder_overrides",
    indices = [Index(value = ["reminderLocalId", "localDate"], unique = true)]
)
data class ReminderOverrideRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderLocalId: Long,
    val localDate: String,
    val minuteOfDay: Int,
    val createdAtUtc: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "occurrences",
    indices = [Index("reminderLocalId"), Index("checkedAtUtc")]
)
data class OccurrenceRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Int? = null,
    val reminderLocalId: Long,
    val dueAtUtc: Long,
    val firedAtUtc: Long = System.currentTimeMillis(),
    val checkedAtUtc: Long? = null,
    val pendingCreate: Boolean = true,
    val pendingCheck: Boolean = false,
)
