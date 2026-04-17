namespace Reminder.Api.Entities;

public class ReminderOccurrence
{
    public int Id { get; set; }
    public int ReminderId { get; set; }
    public Reminder Reminder { get; set; } = null!;

    public DateTime DueAtUtc { get; set; }
    public DateTime FiredAtUtc { get; set; } = DateTime.UtcNow;
    public DateTime? CheckedAtUtc { get; set; }
}
