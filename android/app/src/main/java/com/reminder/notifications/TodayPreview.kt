package com.reminder.notifications

import com.reminder.data.ReminderOverrideRow
import com.reminder.data.ReminderRow
import com.reminder.data.ScheduleKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class TodayItem(
    val reminderLocalId: Long,
    val description: String,
    val minuteOfDay: Int,
    val kind: ScheduleKind,
) {
    val timeLabel: String get() = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}

/**
 * Every active item scheduled for the local day that contains [nowMillis]:
 * all Daily reminders, plus OneTime reminders whose due moment falls inside that day.
 * Sorted by local minute-of-day.
 */
fun todayItems(
    reminders: List<ReminderRow>,
    nowMillis: Long,
    overrides: List<ReminderOverrideRow> = emptyList(),
): List<TodayItem> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
    val todayStr = today.toString()
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val nextDayStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val overrideForToday: Map<Long, Int> = overrides
        .asSequence()
        .filter { it.localDate == todayStr }
        .associate { it.reminderLocalId to it.minuteOfDay }

    val todayBit = 1 shl (today.dayOfWeek.value % 7)

    return reminders.asSequence()
        .filter { it.isActive && !it.pendingDelete }
        .mapNotNull { r ->
            when (r.scheduleKind) {
                ScheduleKind.Daily -> r.dailyMinuteOfDay?.let { base ->
                    val minute = overrideForToday[r.id] ?: base
                    TodayItem(r.id, r.description, minute, r.scheduleKind)
                }
                ScheduleKind.OneTime -> r.oneTimeDueAtUtc
                    ?.takeIf { it in dayStart until nextDayStart }
                    ?.let { due ->
                        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(due), zone)
                        val mod = ldt.hour * 60 + ldt.minute
                        TodayItem(r.id, r.description, mod, r.scheduleKind)
                    }
                ScheduleKind.Weekly -> {
                    val minute = r.dailyMinuteOfDay
                    val mask = r.weeklyDaysMask
                    if (minute != null && mask != null && (mask and todayBit) != 0) {
                        TodayItem(r.id, r.description, minute, r.scheduleKind)
                    } else null
                }
            }
        }
        .sortedBy { it.minuteOfDay }
        .toList()
}

/** Next 08:00 local after [fromMillis]. */
fun nextPreviewAtUtc(fromMillis: Long, previewHour: Int = 8): Long {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone)
    val todayAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(previewHour, 0))
    val target = if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    return target.atZone(zone).toInstant().toEpochMilli()
}
