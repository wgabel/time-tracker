# AGENTS.md — Time Tracker

## Project purpose
Native Android app for tracking time spent on one activity by two people on **one phone**, offline. Each person has an independent START/STOP button. Every session records its start and end timestamps, and the UI shows daily totals and session history. Data should remain available across the year and beyond.

## Current repository state
- Kotlin Android project, module `app`, package/application ID `pl.wgabel.timetracker`.
- Currently a minimal native Android Activity using programmatic views (not Jetpack Compose yet).
- Local SQLite database via `SQLiteOpenHelper` in `MainActivity.kt` (not Room yet).
- Database: `sessions.db`, table `sessions(id, person, started_at, ended_at)`; timestamps are Unix epoch **milliseconds**; `ended_at = NULL` denotes an active session.
- Partial unique index prevents more than one active session per person.
- UI shows two independent toggles, live elapsed time, today's totals and sessions.
- `.github/workflows/android.yml` builds a debug APK with Gradle and uploads it as a workflow artifact.
- The first GitHub Actions build has **not been verified**. Do not claim the APK builds or works until CI proves it.

## Product requirements and invariants
1. Exactly two independently operable people for the MVP; both timers may run simultaneously.
2. START creates a persisted session immediately; STOP closes that same session. Never create overlapping active sessions for the same person.
3. Keep the complete event/session history; never replace it with daily aggregates only.
4. Derive daily, monthly and annual totals from sessions. Sessions spanning midnight must contribute the correct duration to each local calendar day.
5. Timers must remain logically active while the screen is off or the app process is killed; calculate elapsed time from persisted timestamps rather than relying on periodic ticks.
6. Never silently discard or reset sessions on app updates, crashes or database migrations.
7. UI must remain simple: one button per person, clear active state, daily total and accessible history.
8. Work offline without accounts, backend or cloud synchronization.
9. Prioritize correct timing, persistence and data integrity over visual polish.

## Planned improvements (not yet implemented)
- Calendar/day navigation, monthly/yearly summaries and comparison views.
- Rename people and optionally name the tracked activity.
- Manual correction of mistaken sessions with clear validation.
- CSV export and reliable backup/restore.
- Consider migration to Room, ViewModel and Jetpack Compose only with a tested database migration and preserved data.
- Proper release signing and update strategy. Debug APKs signed by different build environments may not install as updates; **do not tell users to uninstall** without explaining data-loss risks.

## Implementation guidance
- Work primarily in `app/src/main/java/pl/wgabel/timetracker/MainActivity.kt` until refactoring is justified.
- Use epoch milliseconds for persisted instants and the device's local time zone for daily grouping. Handle daylight-saving changes correctly by using calendar-day boundaries, not fixed 24-hour offsets.
- Keep database writes transactional. Validate `ended_at >= started_at`.
- Use parameterized SQL for values.
- Keep UI responsive; as the app grows, move DB work off the main thread.
- Avoid destructive schema migrations. Preserve the existing `sessions` data and index.
- Avoid unnecessary permissions, analytics, tracking SDKs and network access.
- Add tests for simultaneous timers, repeated taps, process recreation, midnight crossing, DST boundaries and session aggregation.
- Before changing Gradle or dependencies, check compatibility with the Android Gradle Plugin, Kotlin, JDK and CI runner.
- Keep GitHub Actions building an installable APK and publishing a downloadable artifact.

## Agent workflow
1. Read this file and inspect the current code before editing.
2. Make the smallest coherent change that fulfills the request.
3. Build/test where tools permit; clearly report what was and wasn't verified.
4. Document behavior changes and any migration or signing implications.
5. Never fabricate successful CI results, APKs or completed tests.

## Development environment
The owner currently works from an Android phone, without a computer. Prefer workflows that can be executed from GitHub on mobile, with GitHub Actions building the APK. Avoid requiring Android Studio for routine updates.
