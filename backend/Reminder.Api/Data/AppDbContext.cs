using Microsoft.EntityFrameworkCore;
using Reminder.Api.Entities;

namespace Reminder.Api.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

    public DbSet<Entities.Reminder> Reminders => Set<Entities.Reminder>();
    public DbSet<ReminderOccurrence> Occurrences => Set<ReminderOccurrence>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<Entities.Reminder>(e =>
        {
            e.ToTable("reminders");
            e.HasKey(x => x.Id);
            e.Property(x => x.Description).IsRequired().HasMaxLength(2000);
            e.Property(x => x.ScheduleKind).HasConversion<int>();
            e.HasIndex(x => x.IsActive);
        });

        b.Entity<ReminderOccurrence>(e =>
        {
            e.ToTable("reminder_occurrences");
            e.HasKey(x => x.Id);
            e.HasOne(x => x.Reminder)
                .WithMany(r => r.Occurrences)
                .HasForeignKey(x => x.ReminderId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasIndex(x => new { x.ReminderId, x.CheckedAtUtc });
            e.HasIndex(x => x.DueAtUtc);
        });
    }
}
