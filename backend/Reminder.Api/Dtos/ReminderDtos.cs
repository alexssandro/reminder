using Reminder.Api.Entities;

namespace Reminder.Api.Dtos;

public record CreateReminderRequest(
    string Description,
    ScheduleKind ScheduleKind,
    int? DailyMinuteOfDay,
    DateTime? OneTimeDueAtUtc,
    int? WeeklyDaysMask,
    int? MonthlyDayOfMonth);

public record UpdateReminderRequest(
    string Description,
    ScheduleKind ScheduleKind,
    int? DailyMinuteOfDay,
    DateTime? OneTimeDueAtUtc,
    int? WeeklyDaysMask,
    int? MonthlyDayOfMonth,
    bool IsActive);

public record ReminderDto(
    int Id,
    string Description,
    ScheduleKind ScheduleKind,
    int? DailyMinuteOfDay,
    DateTime? OneTimeDueAtUtc,
    int? WeeklyDaysMask,
    int? MonthlyDayOfMonth,
    bool IsActive,
    DateTime CreatedAtUtc);

public record FireOccurrenceRequest(DateTime DueAtUtc);

public record OccurrenceDto(
    int Id,
    int ReminderId,
    string ReminderDescription,
    DateTime DueAtUtc,
    DateTime FiredAtUtc,
    DateTime? CheckedAtUtc);
