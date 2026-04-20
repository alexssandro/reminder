using Microsoft.EntityFrameworkCore;
using Reminder.Api.Data;
using Reminder.Api.Dtos;
using Reminder.Api.Entities;

namespace Reminder.Api.Endpoints;

public static class ReminderEndpoints
{
    public static void MapReminderEndpoints(this IEndpointRouteBuilder app)
    {
        var r = app.MapGroup("/reminders");

        r.MapGet("/", async (AppDbContext db) =>
        {
            var items = await db.Reminders
                .OrderByDescending(x => x.IsActive)
                .ThenBy(x => x.Id)
                .Select(x => new ReminderDto(
                    x.Id, x.Description, x.ScheduleKind, x.DailyMinuteOfDay,
                    x.OneTimeDueAtUtc, x.WeeklyDaysMask, x.IsActive, x.CreatedAtUtc))
                .ToListAsync();
            return Results.Ok(items);
        });

        r.MapPost("/", async (CreateReminderRequest req, AppDbContext db) =>
        {
            if (string.IsNullOrWhiteSpace(req.Description))
                return Results.BadRequest(new { error = "Description is required" });

            var minuteRequired = req.ScheduleKind == ScheduleKind.Daily || req.ScheduleKind == ScheduleKind.Weekly;
            if (minuteRequired &&
                (req.DailyMinuteOfDay is null || req.DailyMinuteOfDay < 0 || req.DailyMinuteOfDay > 1439))
                return Results.BadRequest(new { error = "DailyMinuteOfDay required in 0..1439" });

            if (req.ScheduleKind == ScheduleKind.OneTime && req.OneTimeDueAtUtc is null)
                return Results.BadRequest(new { error = "OneTimeDueAtUtc required" });

            if (req.ScheduleKind == ScheduleKind.Weekly &&
                (req.WeeklyDaysMask is null || (req.WeeklyDaysMask & 0x7F) == 0))
                return Results.BadRequest(new { error = "WeeklyDaysMask required (bits 0..6, at least one day)" });

            var entity = new Entities.Reminder
            {
                Description = req.Description.Trim(),
                ScheduleKind = req.ScheduleKind,
                DailyMinuteOfDay = minuteRequired ? req.DailyMinuteOfDay : null,
                OneTimeDueAtUtc = req.ScheduleKind == ScheduleKind.OneTime ? req.OneTimeDueAtUtc : null,
                WeeklyDaysMask = req.ScheduleKind == ScheduleKind.Weekly ? (req.WeeklyDaysMask & 0x7F) : null,
                IsActive = true,
            };
            db.Reminders.Add(entity);
            await db.SaveChangesAsync();
            return Results.Created($"/reminders/{entity.Id}", ToDto(entity));
        });

        r.MapPut("/{id:int}", async (int id, UpdateReminderRequest req, AppDbContext db) =>
        {
            var e = await db.Reminders.FindAsync(id);
            if (e is null) return Results.NotFound();
            var minuteRequired = req.ScheduleKind == ScheduleKind.Daily || req.ScheduleKind == ScheduleKind.Weekly;
            e.Description = req.Description.Trim();
            e.ScheduleKind = req.ScheduleKind;
            e.DailyMinuteOfDay = minuteRequired ? req.DailyMinuteOfDay : null;
            e.OneTimeDueAtUtc = req.ScheduleKind == ScheduleKind.OneTime ? req.OneTimeDueAtUtc : null;
            e.WeeklyDaysMask = req.ScheduleKind == ScheduleKind.Weekly ? (req.WeeklyDaysMask & 0x7F) : null;
            e.IsActive = req.IsActive;
            await db.SaveChangesAsync();
            return Results.Ok(ToDto(e));
        });

        r.MapDelete("/{id:int}", async (int id, AppDbContext db) =>
        {
            var e = await db.Reminders.FindAsync(id);
            if (e is null) return Results.NotFound();
            db.Reminders.Remove(e);
            await db.SaveChangesAsync();
            return Results.NoContent();
        });

        r.MapPost("/{id:int}/fire", async (int id, FireOccurrenceRequest req, AppDbContext db) =>
        {
            var reminder = await db.Reminders.FindAsync(id);
            if (reminder is null) return Results.NotFound();
            var occ = new ReminderOccurrence
            {
                ReminderId = id,
                DueAtUtc = req.DueAtUtc,
                FiredAtUtc = DateTime.UtcNow,
            };
            db.Occurrences.Add(occ);
            await db.SaveChangesAsync();
            return Results.Ok(new OccurrenceDto(
                occ.Id, occ.ReminderId, reminder.Description,
                occ.DueAtUtc, occ.FiredAtUtc, occ.CheckedAtUtc));
        });

        var o = app.MapGroup("/occurrences");

        o.MapGet("/pending", async (AppDbContext db) =>
        {
            var items = await db.Occurrences
                .Where(x => x.CheckedAtUtc == null)
                .OrderBy(x => x.DueAtUtc)
                .Select(x => new OccurrenceDto(
                    x.Id, x.ReminderId, x.Reminder.Description,
                    x.DueAtUtc, x.FiredAtUtc, x.CheckedAtUtc))
                .ToListAsync();
            return Results.Ok(items);
        });

        o.MapPost("/{id:int}/check", async (int id, AppDbContext db) =>
        {
            var e = await db.Occurrences.Include(x => x.Reminder).FirstOrDefaultAsync(x => x.Id == id);
            if (e is null) return Results.NotFound();
            if (e.CheckedAtUtc is null)
            {
                e.CheckedAtUtc = DateTime.UtcNow;
                await db.SaveChangesAsync();
            }
            return Results.Ok(new OccurrenceDto(
                e.Id, e.ReminderId, e.Reminder.Description,
                e.DueAtUtc, e.FiredAtUtc, e.CheckedAtUtc));
        });
    }

    private static ReminderDto ToDto(Entities.Reminder e) =>
        new(e.Id, e.Description, e.ScheduleKind, e.DailyMinuteOfDay,
            e.OneTimeDueAtUtc, e.WeeklyDaysMask, e.IsActive, e.CreatedAtUtc);
}
