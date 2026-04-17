namespace Reminder.Api.Entities;

public enum ScheduleKind
{
    Daily = 1,
    OneTime = 2
}

public class Reminder
{
    public int Id { get; set; }
    public string Description { get; set; } = string.Empty;
    public ScheduleKind ScheduleKind { get; set; }

    // Daily: HH:mm local time-of-day (stored as minutes since midnight, 0..1439)
    public int? DailyMinuteOfDay { get; set; }

    // OneTime: UTC moment
    public DateTime? OneTimeDueAtUtc { get; set; }

    public bool IsActive { get; set; } = true;
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;

    public List<ReminderOccurrence> Occurrences { get; set; } = new();
}
