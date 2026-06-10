# CLAUDE.md

Conventions for this repo. Keep this short and factual.

## Architecture

- **No auth.** Single-user app. All endpoints are open.
- **Backend = thin CRUD + occurrence log.** Android is the source of scheduling truth.
- **Android is local-first.** Every mutation lands in Room first and is marked pending; `SyncManager` pushes and pulls when the network is available. Do not bypass Room from the UI.

## Data model

- `ScheduleKind` is persisted as an int (1 = Daily, 2 = OneTime, 3 = Weekly, 4 = Anytime, 5 = Monthly) on both sides — keep the mapping in sync between `Reminder.Api/Entities/Reminder.cs` and `com.reminder.data.ScheduleKind`.
- `Daily` stores `DailyMinuteOfDay` as minutes since local midnight (0..1439). `Weekly` adds `WeeklyDaysMask` (bit 0 = Sunday … bit 6 = Saturday).
- `OneTime` stores `OneTimeDueAtUtc` as a UTC `DateTime` (C# side) / epoch-millis (Android side) — serialize as ISO-8601 on the wire.
- `Anytime` stores no schedule fields (all null). It never fires an alarm; Home keeps it always available to check off until checked for the day.
- `Monthly` stores `MonthlyDayOfMonth` (1..31); months with fewer days clamp to their last day. Like Anytime it never fires an alarm — it's available to check off only on that day each month.

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
- `scheduleNextAfter` also sets pre-fire countdown notifications at 1h/30m/10m before the primary (`ACTION_PRE`); they reuse one notification id per reminder so they replace each other, and are cleared when the primary fires.
- The daily preview (`DailyPreviewScheduler`, slots 9/12/18/21 local) lists today's still-open items (timed + Anytime + Monthly). It skips reminders already checked today and posts nothing when the list is empty. Each fire chains the next slot.

## Dev workflow

- `android/deploy.ps1` builds the debug APK, installs it on the connected device, and cold-starts the app with `--ez fire_preview true` so the daily preview fires as a smoke check. The `fire_preview` hook is read only in `onCreate`, so a cold start (force-stop first) is required.
- Debug builds expose a "Send test notification" button at the bottom of the Manage screen; it calls the same `DailyPreviewReceiver.buildAndShow` path and toasts whether the preview fired or was skipped.

## Secrets

- `.env` at repo root (gitignored) for docker-compose.
- `appsettings.Development.json` + `dotnet user-secrets` for local dev.
- `android/local.properties` for the Android API base URL.
