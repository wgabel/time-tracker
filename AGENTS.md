# AGENTS.md — timetrkr (time-tracker)

## Mission and product
Native, offline-first Android app for recording how long different caregivers spend caring for a child (also usable for other activities). The user works mainly from an Android phone and expects agents to **implement requested changes directly in this GitHub repository**, not merely describe how to do them. Communicate with the user in Polish. Prioritize factual accuracy, preservation of data, minimal friction, and working builds over speculative improvements.

Repository: `wgabel/time-tracker`, default branch `main`. App branding: **timetrkr**; package/application ID: `pl.wgabel.timetracker`.

## Actual implementation (inspect code; this document is a guide, not a substitute)
- Android app in Kotlin, programmatic native Views (not XML screen layouts, Compose, or React).
- Primary Activity and most business logic: `app/src/main/java/pl/wgabel/timetracker/MainActivity.kt`.
- Persistent foreground timer notification: `app/src/main/java/pl/wgabel/timetracker/TimerNotificationService.kt`; notification layout in `app/src/main/res/layout/notification_timer.xml`.
- SQLite via `SQLiteOpenHelper` in `MainActivity.onCreate`, database file `sessions.db`, **schema version 6** as of 2026-11-14.
- SharedPreferences `preferences` store UI choices including theme and language.
- Android resource strings in `app/src/main/res/values/strings.xml` and `values-pl/strings.xml`. Existing code also contains hard-coded Polish labels, especially for the new EVENT feature; localize these before expanding language support.
- Gradle Kotlin DSL: `app/build.gradle.kts`, root `build.gradle.kts`, `settings.gradle.kts`. As currently configured: compileSdk/targetSdk 35, minSdk 26, Java/Kotlin JVM target 17, versionName generated as `0.1.<run>-<color>-<animal>` (see Gradle file).
- GitHub Actions: `.github/workflows/android.yml`. Push to main or workflow_dispatch builds **signed release** APK with Java 17, Gradle 8.9 and Android SDK 35. Artifact `timetrkr-release` contains `timetrkr-release.apk`. Secrets: `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD`; keystore alias `time-tracker`. VersionCode uses `1000 + GITHUB_RUN_NUMBER` in CI.
- Check latest workflow run and its logs after committing code. A successful commit is not proof of a successful build. Check the latest commit-specific Actions result; older successful runs do not validate newer changes. A CI pass is not a device test.

## Existing product behavior
- Projects: create, rename, archive, and choose a generated project icon. A project has multiple participants (not limited to two). Archived projects are viewable with restrictions on edits.
- Participants: add, rename and remove subject to code's safeguards; each has a stable numeric database ID.
- Timers tab: independent START/STOP for each participant; multiple people may be active simultaneously. Displays selected day's total, live running duration, and recent sessions. Navigation across calendar days.
- Statistics tab: period navigation and visualizations including bar chart, participant comparison/pie, and timeline. Consult code for exact periods and aggregation.
- History tab: day navigation, participant filtering, timeline, session list, manual session creation, session editing/deletion (when permitted).
- Settings: theme (light/dark), language, CSV export, project/participant controls. Inspect `renderSettings` and `renderAppSettings` for exact behavior.
- Active cards use an animated green-tinted gradient and floating squares. Drawable implements `Animatable`; animation must not restart every second.
- Each participant's avatar initials, timer START button, chart/timeline markings use the same `personColor(person)` palette derived from participant ID, not participant list order. STOP is red. Palette is defined once in `MainActivity.kt`.
- Events (introduced 2026-11-11): while a timer is active, EVENT beside STOP captures timestamp **immediately**, then opens an editor for type and note. Types currently hardcoded: `Lek`, `Posiłek`, `Sen`, `Spacer`, `Inne`. Medication type requires a nonempty free-text note (intended for name, concentration and administered dose). Events display beneath sessions in Timers and History; History allows editing type/note and deletion with confirmation, and adding an event to a completed session with a date/time/seconds picker constrained to the session's timestamps. Existing-event timestamp editing is not implemented. Events are **not** included in timing statistics/charts. Event editing currently does **not** change timestamp; adding events outside sessions is not implemented. Do not claim otherwise.

## Database schema (v6)
```sql
CREATE TABLE projects (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL,
 archived INTEGER NOT NULL DEFAULT 0,
 icon_variant INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE participants (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 project_id INTEGER NOT NULL,
 name TEXT NOT NULL,
 color INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE sessions (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 project_id INTEGER NOT NULL,
 person INTEGER NOT NULL,
 started_at INTEGER NOT NULL,
 ended_at INTEGER
);
CREATE UNIQUE INDEX one_active_per_person ON sessions(person)
 WHERE ended_at IS NULL;
CREATE TABLE events (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 project_id INTEGER NOT NULL,
 session_id INTEGER NOT NULL,
 person INTEGER NOT NULL,
 happened_at INTEGER NOT NULL,
 type TEXT NOT NULL,
 note TEXT NOT NULL
);
```
All timestamps are Unix epoch **milliseconds**. `ended_at IS NULL` means active. `person` is a participant ID. Current schema does not declare foreign keys or ON DELETE CASCADE. **Review event referential integrity** when deleting sessions, participants, or projects: the initial EVENT implementation does not automatically remove/reattach dependent events. Avoid orphaned data and accidental loss. If extending to standalone events, make `session_id` nullable via a safe migration; it is currently NOT NULL.

Database upgrades are incremental in `onUpgrade` (legacy versions 1–5 to v6); v6 adds `participants.color` and backfills legacy colors. Never drop/recreate existing tables to add a feature. Existing installations and their data must survive updates. Preserve uniqueness constraint and project isolation. Parameterize SQL values; validate start/end times and session associations.

## Timing and rendering invariants
1. START persists a session; STOP persists `ended_at`. Never allow two active sessions for the same participant. Simultaneous sessions for different participants are supported.
2. Timers derive elapsed time from persisted epoch timestamps, not from counting Handler ticks. Timers must remain accurate after app backgrounding, process death, and screen-off.
3. Local calendar boundaries matter. Use `Calendar`/time-zone-aware boundaries for midnight and DST, not a fixed 86,400,000 ms day.
4. `MainActivity` has a one-second Handler tick. It updates existing text labels via `timerUpdates` callbacks, rather than rebuilding the whole view every second. `renderContent()` clears/recreates views for actual state/navigation changes. **Do not reintroduce a once-per-second `renderContent()` call**: that restarts animated active-card backgrounds and causes visible jitter.
5. `activeCardBackground()` uses an animated Drawable; `Drawable.setCallback` is final and MUST NOT be overridden. Its `Animatable` lifecycle is coordinated with view attachment/detachment.
6. Maintain participant color consistency via `personColor(person)` in avatar, timers, charts and timeline; do not revert to alternating blue/green by position.
7. Never silently reset or delete existing sessions/events, even when changing visual UI or database schema.

## Current UI interaction conventions (inspect live code first)
- Project list rows show a right-facing chevron `›` as a persistent interaction affordance.
- History session rows, History event rows, and participant rows in project Settings reveal contextual actions on tap with a ~260 ms easing animation. Their chevrons remain visible and rotate ~90 degrees to indicate expansion; **never fade out or hide the chevron when expanded**.
- Session actions are in a tray below the row (add historical event, edit, delete); event actions slide into the row on the right (edit, delete); participant actions slide into the row on the right (color, rename, delete). Preserve different layouts where intentional.
- History events use small category-specific, background-free outline icons. Use the existing drawing helper instead of emoji or a new icon family.
- History and Statistics use a date navigator pinned under the persistent top header, outside the scrolling content; changing dates must update the corresponding view without losing the sticky behavior.
- Reuse the existing `label`, `card`, `styledAction`, `iconButton`, `shape`, `dp`, theme colors, and `ValueAnimator` conventions. Respect dark/light modes and touch targets. Do not add unrelated UI redesigns.
- When proposing a preview/mockup, do not commit it unless the user asks to implement it. When explicitly asked to implement, modify the repository directly.

## Events: design and safety considerations
- Events are point-in-time annotations attached to a specific care session and participant. Adding an event must not pause, stop, reset, split, or otherwise alter the running timer.
- Capture `happened_at` at the button press, **before** the user types details.
- Medication logging is a record of **what was actually administered**, not a dosing calculator, clinical recommendation, or automatic confirmation that administration was safe. Avoid implying that any dose or interval is medically approved.
- The current medication description is unstructured free text. Future structured medication fields, history checks, duplicate-dose warnings and multi-device synchronization require explicit design, schema migrations, and tests.
- The app is currently local-only, single-device, without accounts or synchronization. Do not imply one caregiver's phone automatically receives another's entries.
- Current limitations to consider when relevant: no standalone event creation; no timestamp adjustment when editing an existing event (new historical events have a constrained picker); no event export verified; no automatic orphan cleanup; event editor labels are hard-coded Polish. Treat these as limitations, not completed features.

## CSV export and backup
`exportCsv()` exists; inspect its SQL and output columns before changing it. **Do not assume EVENT entries are exported**: they were added after the export implementation. CSV export is not equivalent to a complete database backup. Be cautious with reinstall/uninstall and signing key changes: uninstalling may erase the local SQLite database. Never advise uninstalling as a casual fix.

## Engineering quality and code composition (mandatory)
- Apply established Kotlin/Android software engineering practices to every change: readable names, clear responsibilities, cohesive functions, appropriate visibility, null safety, and consistent error handling.
- Prefer small, composable, reusable helpers over duplicated logic or new responsibilities inside the monolithic Activity. Separate presentation/formatting, persistence, and UI behavior when doing so reduces coupling without risky refactors.
- Follow single-responsibility and separation-of-concerns principles pragmatically; avoid overengineering, speculative abstractions, dead code, and unnecessary dependencies.
- Keep localized user-facing dates/times in a shared formatting layer using the effective in-app language (including system-language mode). Avoid hardcoded date ordering and cached formatters tied to a previous locale; keep stored epoch timestamps and machine-readable CSV formats stable.
- Check all affected UI surfaces, including dialogs, descriptions, notifications, accessibility text, and export formats, whenever changing localization or formatting.
- Review edge cases and compatibility, add focused tests where practical, and preserve backward-compatible persistence and existing behavior. A compiling change is not automatically a well-designed or tested change.

## Agent execution workflow
1. Read `AGENTS.md`, `MainActivity.kt`, relevant service/resources, Gradle config and workflow **at the current main HEAD** before editing. Check existing implementation and don't assume old summaries are current.
2. Identify the user-facing behavior, invariants, data migration and edge cases. Prefer the smallest coherent change; avoid broad refactors unless justified.
3. Edit the connected GitHub repository directly when asked to implement a change. Use the exact current file blob SHA for update; avoid concurrent writes to the same file. Write clear commit messages.
4. Check Kotlin compilation and GitHub Actions run associated with **your commit**, not an older successful run. Poll the new run until completion when feasible; inspect failed job logs and fix compiler errors, then check the next run. If still queued, state verification is pending; never invent successful tests. Example: the sticky date header introduced a Kotlin `Drawable` vs `Int` shadowing error (`setBackgroundColor(background)` inside a `View.apply`); qualify activity properties with `this@MainActivity.background` when necessary.
5. For DB changes, verify both fresh install and upgrade paths; consider existing active sessions, archived projects, and older databases. Add tests when possible (timer concurrency, rapid taps, midnight/DST, aggregation, events and migration).
6. Preserve UI consistency, themes, both supported languages, and existing notification behavior. Avoid unnecessary network permissions, analytics or SDKs.
7. Tell the user in Polish what changed, what was not implemented, commit SHA, build status, and link to the relevant Actions run/APK when available. If CI fails, do not treat the change as delivered until corrected or clearly disclosed.

## Known technical debt / risks
- `MainActivity.kt` is monolithic and performs SQLite work on the UI thread; a future refactor into repository/database/UI layers would help but must not break migrations.
- Events may become orphaned after session deletion; inspect delete paths before making changes involving data integrity.
- Event notes contain potentially sensitive child health information: keep offline, minimize data exposure and avoid exposing details in notifications/logs without clear intent.
- Tests are limited; successful CI compilation is not an emulator/device functional test.
- Android 13+ notification permission is requested; foreground service and notification implementation must be reviewed if Android target requirements change.
- Signed APK update continuity depends on stable signing secrets/keystore. Keep versionCode monotonic.
