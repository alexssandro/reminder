# Reminder

Local-first reminder app with cloud sync.

- **android/** — Kotlin / Jetpack Compose client. Room is the source of truth; a `SyncManager` pushes queued changes and pulls the latest state whenever the network is available.
- **backend/Reminder.Api/** — ASP.NET Core (.NET 10) minimal API. EF Core + PostgreSQL. No auth (single-user app for now).
- **db/** — seed scripts / migrations live here.
- **docker-compose.yml** — runs Postgres + API.

## Screenshots

| Home | Manage |
| --- | --- |
| ![Home](docs/screenshots/home.png) | ![Manage](docs/screenshots/manage.png) |

## Reminder model

- **Description** — free text.
- **ScheduleKind**:
  - `Daily` — fire every day at a given local HH:MM.
  - `Weekly` — fire at HH:MM on every selected day of the week (e.g. Mon, Wed, Fri).
  - `OneTime` — fire at a specific UTC moment.
  - `Anytime` — no due date or alarm; always offered on Home until checked off for the day.
  - `Monthly` — no alarm; available to check off on one chosen day (1–31) each month. Months
    shorter than the chosen day clamp to their last day (31 → Feb 28).
- **Checklists** — a reminder can carry sub-items (local-only, no backend sync). Ticks reset
  each day, independent of the parent check.
- **Occurrences** — each time a reminder fires, we record an occurrence. While an occurrence
  is unchecked, Android re-rings it every hour. Check-offs play a confetti animation.

## Notifications

- **Primary fire** at the scheduled moment, preceded by countdown notifications at 1h / 30m /
  10m before. Unchecked occurrences re-ring hourly until checked.
- **Daily preview** at 9:00, 12:00, 18:00, and 21:00 local time lists everything still open
  today (timed + Anytime + Monthly). Items already checked off are skipped, and if nothing is
  left the notification is not shown at all.

## UI

Terminal-styled: Cascadia Mono everywhere, Windows Terminal "Campbell" palette on near-black,
prompt-style headers (`reminder:~$` with a blinking cursor). Home reads like a TODO list —
`[!]` needs attention, `[ ]` open, `[x]` done — with a pending/done count and ASCII progress
bar in the header; checked items collapse into a single `[+] checked (N)` line.

## Running the backend

```bash
cp .env.example .env          # adjust values
docker compose up -d          # postgres on :5433, api on :8081
```

For local dev without Docker:

```bash
cd backend/Reminder.Api
dotnet user-secrets set "ConnectionStrings:Postgres" "Host=localhost;Port=5433;Database=reminder;Username=reminder;Password=change_me"
dotnet run     # http://0.0.0.0:5080
```

## Running the Android app

1. `cp android/local.properties.example android/local.properties` and set `sdk.dir` + `reminder.api.baseUrl`.
    - Emulator → `http://10.0.2.2:5080/`
    - Physical device → `http://<your-LAN-IP>:5080/`
2. Open `android/` in Android Studio and Run — or use the one-command deploy:

```powershell
.\android\deploy.ps1   # build, install on the connected device, cold-start with a
                       # fired daily preview as a post-deploy smoke check
```

Debug builds also have a **Send test notification** button at the bottom of the Manage screen;
it runs the real preview path and reports via toast whether the notification fired or was
skipped because everything was already checked off.

## Offline behavior

- Creating, editing, deleting reminders and checking off occurrences works fully offline — each write hits Room immediately and flags the row `pendingCreate` / `pendingUpdate` / `pendingDelete` / `pendingCheck`.
- When connectivity is reported, `SyncManager` pushes each queued change and pulls the server's current state. Conflicts resolve local-wins if the row still has pending flags, server-wins otherwise.
- Alarms fire via `AlarmManager` regardless of network state. The hourly re-ring is also a local alarm, so un-checked reminders keep nagging you offline.
