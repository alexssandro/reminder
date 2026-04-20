namespace Reminder.Api.Entities;

public enum ScheduleKind
{
    Daily = 1,
    OneTime = 2,
    Weekly = 3
}

public class Reminder
{
    public int Id { get; set; }
    public string Description { get; set; } = string.Empty;
    public ScheduleKind ScheduleKind { get; set; }

    // Daily & Weekly: HH:mm local time-of-day (stored as minutes since midnight, 0..1439)
    public int? DailyMinuteOfDay { get; set; }

    // OneTime: UTC moment
    public DateTime? OneTimeDueAtUtc { get; set; }

    // Weekly: bitmask with bit 0 = Sunday, 1 = Monday, ..., 6 = Saturday
    public int? WeeklyDaysMask { get; set; }

    public bool IsActive { get; set; } = true;
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;

    public List<ReminderOccurrence> Occurrences { get; set; } = new();
}
