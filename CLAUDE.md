# CLAUDE.md

Conventions for this repo. Keep this short and factual.

## Architecture

- **No auth.** Single-user app. All endpoints are open.
- **Backend = thin CRUD + occurrence log.** Android is the source of scheduling truth.
- **Android is local-first.** Every mutation lands in Room first and is marked pending; `SyncManager` pushes and pulls when the network is available. Do not bypass Room from the UI.

## Data model

- `ScheduleKind` is persisted as an int (1 = Daily, 2 = OneTime) on both sides — keep the mapping in sync between `Reminder.Api/Entities/Reminder.cs` and `com.reminder.data.ScheduleKind`.
- `Daily` stores `DailyMinuteOfDay` as minutes since local midnight (0..1439).
- `OneTime` stores `OneTimeDueAtUtc` as a UTC `DateTime` (C# side) / epoch-millis (Android side) — serialize as ISO-8601 on the wire.

## Migrations

EF Core migrations auto-apply on startup via `db.Database.Migrate()` in `Program.cs`. When the schema changes:

```bash
cd backend/Reminder.Api
dotnet ef migrations add <Name>
```

## Alarms / re-reminders

- `ReminderScheduler.scheduleNext` schedules the *primary* fire.
- When an alarm fires, `ReminderReceiver` records an occurrence, shows the notification, and schedules a +1h `ACTION_REPEAT`. The repeat chain continues until `ReminderActionReceiver.ACTION_CHECK` (from the "Mark done" button) or the in-app check cancels it.
- Daily reminders also chain their *next day* primary fire from the receiver — don't rely on repeating alarms; each fire schedules the one after it.

## Secrets

- `.env` at repo root (gitignored) for docker-compose.
- `appsettings.Development.json` + `dotnet user-secrets` for local dev.
- `android/local.properties` for the Android API base URL.
