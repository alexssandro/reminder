package com.reminder.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.reminder.data.AppDatabase
import com.reminder.data.ReminderOverrideDao
import com.reminder.data.ReminderRow
import com.reminder.data.ScheduleKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Schedules the *next* fire via AlarmManager for a given reminder. After it fires, the
 * ReminderReceiver re-schedules either (a) the next daily occurrence, or (b) nothing for
 * one-time reminders. The hourly re-reminder chain is scheduled separately by
 * scheduleRepeatReminder when an occurrence goes un-checked.
 */
object ReminderScheduler {

    suspend fun scheduleNext(ctx: Context, reminder: ReminderRow) {
        val triggerAtUtc = nextFireAtUtc(
            reminder,
            System.currentTimeMillis(),
            AppDatabase.get(ctx).overrides(),
        ) ?: run {
            cancel(ctx, reminder.id)
            return
        }
        setAlarm(ctx, requestCodeFor(reminder.id, REQ_PRIMARY), triggerAtUtc,
            primaryIntent(ctx, reminder.id, triggerAtUtc))
    }

    /** After the alarm fires and the occurrence stays un-checked for an hour, re-ring. */
    fun scheduleRepeatReminder(ctx: Context, reminderLocalId: Long, occurrenceLocalId: Long, triggerAtUtc: Long) {
        setAlarm(ctx, requestCodeFor(occurrenceLocalId, REQ_REPEAT), triggerAtUtc,
            repeatIntent(ctx, reminderLocalId, occurrenceLocalId, triggerAtUtc))
    }

    fun cancelRepeat(ctx: Context, occurrenceLocalId: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, requestCodeFor(occurrenceLocalId, REQ_REPEAT),
            Intent(ctx, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_REPEAT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    fun cancel(ctx: Context, reminderLocalId: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, requestCodeFor(reminderLocalId, REQ_PRIMARY),
            Intent(ctx, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_FIRE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    private fun primaryIntent(ctx: Context, reminderLocalId: Long, triggerAtUtc: Long): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_REMINDER_LOCAL_ID, reminderLocalId)
            putExtra(ReminderReceiver.EXTRA_DUE_AT_UTC, triggerAtUtc)
        }
        return PendingIntent.getBroadcast(
            ctx, requestCodeFor(reminderLocalId, REQ_PRIMARY), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun repeatIntent(ctx: Context, reminderLocalId: Long, occurrenceLocalId: Long, triggerAtUtc: Long): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REPEAT
            putExtra(ReminderReceiver.EXTRA_REMINDER_LOCAL_ID, reminderLocalId)
            putExtra(ReminderReceiver.EXTRA_OCCURRENCE_LOCAL_ID, occurrenceLocalId)
            putExtra(ReminderReceiver.EXTRA_DUE_AT_UTC, triggerAtUtc)
        }
        return PendingIntent.getBroadcast(
            ctx, requestCodeFor(occurrenceLocalId, REQ_REPEAT), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun setAlarm(ctx: Context, code: Int, triggerAtUtc: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAtUtc, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtUtc, pi)
        }
    }

    /**
     * Next fire moment in epoch-millis, accounting for per-day overrides on Daily reminders.
     * Scans forward up to 60 days (enough headroom even if all overrides pull days around).
     */
    suspend fun nextFireAtUtc(r: ReminderRow, nowMillis: Long, overrides: ReminderOverrideDao): Long? {
        if (!r.isActive) return null
        return when (r.scheduleKind) {
            ScheduleKind.Daily -> {
                val base = r.dailyMinuteOfDay ?: return null
                val zone = ZoneId.systemDefault()
                val now = LocalDateTime.now(zone)
                var date = LocalDate.now(zone)
                repeat(60) {
                    val minute = overrides.findFor(r.id, date.format(ISO_DATE))?.minuteOfDay ?: base
                    val candidate = LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60))
                    if (candidate.isAfter(now)) {
                        return candidate.atZone(zone).toInstant().toEpochMilli()
                    }
                    date = date.plusDays(1)
                }
                null
            }
            ScheduleKind.OneTime -> r.oneTimeDueAtUtc?.takeIf { it > nowMillis }
            ScheduleKind.Weekly -> {
                val minute = r.dailyMinuteOfDay ?: return null
                val mask = r.weeklyDaysMask ?: return null
                if (mask and 0x7F == 0) return null
                val zone = ZoneId.systemDefault()
                val now = LocalDateTime.now(zone)
                var date = LocalDate.now(zone)
                repeat(14) {
                    val bit = 1 shl (date.dayOfWeek.value % 7)
                    if ((mask and bit) != 0) {
                        val candidate = LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60))
                        if (candidate.isAfter(now)) {
                            return candidate.atZone(zone).toInstant().toEpochMilli()
                        }
                    }
                    date = date.plusDays(1)
                }
                null
            }
        }
    }

    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun requestCodeFor(id: Long, salt: Int): Int = ((id shl 4) or salt.toLong()).toInt()
    private const val REQ_PRIMARY = 1
    private const val REQ_REPEAT = 2
}
