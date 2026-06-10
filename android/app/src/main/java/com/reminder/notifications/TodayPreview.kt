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
                // Anytime and Monthly reminders have no time-of-day; they're surfaced separately.
                ScheduleKind.Anytime, ScheduleKind.Monthly -> null
            }
        }
        .sortedBy { it.minuteOfDay }
        .toList()
}

/**
 * True when [r]'s current schedule would still fire at [occDueAtUtc].
 *
 * Used to detect *stale* pending occurrences after an edit: if the answer is false, the
 * reminder has been rescheduled / disabled / re-kinded since this occurrence was recorded,
 * so the home screen should hide it and the alarm chain should be torn down. [override]
 * is the override minute-of-day for the occurrence's local date, if any.
 */
fun reminderFiresAt(r: ReminderRow, occDueAtUtc: Long, override: Int? = null): Boolean {
    if (!r.isActive) return false
    val zone = ZoneId.systemDefault()
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(occDueAtUtc), zone)
    val occMinute = ldt.hour * 60 + ldt.minute
    return when (r.scheduleKind) {
        ScheduleKind.Daily -> (override ?: r.dailyMinuteOfDay) == occMinute
        ScheduleKind.Weekly -> {
            val mask = r.weeklyDaysMask ?: 0
            val bit = 1 shl (ldt.toLocalDate().dayOfWeek.value % 7)
            (mask and bit) != 0 && r.dailyMinuteOfDay == occMinute
        }
        ScheduleKind.OneTime -> r.oneTimeDueAtUtc == occDueAtUtc
        // No scheduled fire time, so no occurrence is ever pinned to one.
        ScheduleKind.Anytime, ScheduleKind.Monthly -> false
    }
}

/**
 * True when a Monthly reminder set to [dayOfMonth] (1..31) is available on [date].
 * Months shorter than [dayOfMonth] clamp to their last day (e.g. 31 → Feb 28).
 */
fun monthlyAvailableOn(date: LocalDate, dayOfMonth: Int): Boolean =
    date.dayOfMonth == minOf(dayOfMonth, date.lengthOfMonth())

/** Soonest local preview slot (one of [hours], on-the-hour) strictly after [fromMillis]. */
fun nextPreviewAtUtc(fromMillis: Long, hours: List<Int>): Long {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone)
    val today = now.toLocalDate()
    // Scan today's and tomorrow's slots; tomorrow guarantees a hit once today's are all past.
    val next = (0L..1L)
        .flatMap { dayOffset -> hours.map { h -> LocalDateTime.of(today.plusDays(dayOffset), LocalTime.of(h, 0)) } }
        .filter { it.isAfter(now) }
        .minOrNull()!!
    return next.atZone(zone).toInstant().toEpochMilli()
}
