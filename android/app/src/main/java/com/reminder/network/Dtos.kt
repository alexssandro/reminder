package com.reminder.network

import kotlinx.serialization.Serializable

@Serializable
data class ReminderDto(
    val id: Int,
    val description: String,
    val scheduleKind: Int,
    val dailyMinuteOfDay: Int? = null,
    val oneTimeDueAtUtc: String? = null,
    val weeklyDaysMask: Int? = null,
    val isActive: Boolean,
    val createdAtUtc: String,
)

@Serializable
data class CreateReminderRequest(
    val description: String,
    val scheduleKind: Int,
    val dailyMinuteOfDay: Int? = null,
    val oneTimeDueAtUtc: String? = null,
    val weeklyDaysMask: Int? = null,
)

@Serializable
data class UpdateReminderRequest(
    val description: String,
    val scheduleKind: Int,
    val dailyMinuteOfDay: Int? = null,
    val oneTimeDueAtUtc: String? = null,
    val weeklyDaysMask: Int? = null,
    val isActive: Boolean,
)

@Serializable
data class FireOccurrenceRequest(
    val dueAtUtc: String,
)

@Serializable
data class OccurrenceDto(
    val id: Int,
    val reminderId: Int,
    val reminderDescription: String,
    val dueAtUtc: String,
    val firedAtUtc: String,
    val checkedAtUtc: String? = null,
)
