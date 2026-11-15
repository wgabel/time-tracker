# Architecture after PR #27

The app remains a single-module, offline Android application using programmatic native Views and SQLite. Its application ID, Android Activity/service component names, database filename, schema version 6, preferences, design-token values, resources and notification layout remain compatible with existing installations.

## Ownership and dependencies

- `MainActivity` hosts Android lifecycle, permission requests, the document picker and the one-second clock callback. It contains no feature rendering or SQLite access.
- `TimeTrackerApplication` owns `AppContainer`. The Activity and foreground notification service share its connection and repository instances; destroying an Activity cannot close a connection still used by notifications. Process teardown and existing isolated instrumentation fixtures explicitly release the container before replacing their test database.
- `AppCoordinator` owns selected project/tab, the root layout, persistent header/footer, sticky calendar host and navigation. `AppNavigation` is the feature-to-host contract. Features never depend on `MainActivity`.
- `features/projects` owns the project list, project creation and participant forms. `features/timers` owns the timer screen, immutable `TimersViewState` snapshots, start/stop controller, cards and recent activity. `features/history` owns selected day/participant, manual creation and session rows. `features/statistics` owns selected day/period and chart composition. `features/settings` owns project and application settings.
- `ui/events` owns event forms, icons, contextual actions and session-bound event presentation. Events are embedded in sessions, rather than a new navigation tab. `ui/sessions`, `ui/calendar` and `ui/timeline` contain components shared by the consuming features.
- `ui/sessions/DateTimeEditor` composes the themed native time picker from `DayTimeScale`, `SwipeTimePartControl` and `TimeSelectionView`. A pure `time/DateTimeSelection` is its single draft value: user drags on the rail select roughly in five-minute steps, while fields render and adjust precise hours/minutes. Date, optional seconds and relative-time shortcuts are secondary. Missing DST times cannot be saved; repeated times retain their original offset and offer an occurrence selector. Dialogs call the existing domain-backed callbacks; they never write directly to SQLite.
- Completed-session edits use `time/SessionTimeSelection` to retain independent start/end drafts behind visible boundary selectors. Switching changes the selection rendered by the shared controls and does not write. The dialog validates both timestamps and strict interval ordering before submitting one pair through the existing session callback. Active-session start and event timestamp editing retain their single-value mode.
- `DesignTokens` loads the main `assets/design_tokens.json` at the application boundary. Picker palettes, dimensions, opacity/shape proportions and daylight gradient positions are configured there. `DayTimeScale` renders positioned colors through `LinearGradient`, supported from the app's minimum API 26; it does not assume equal spacing or use the newer gradient-position API. Dawn/sunset are illustrative fixed local times, with no location permission, network service or astronomical calculation.
- `domain/SessionService` applies participant/project edit guards, coordinates timer transitions and reports successful mutations to notification coordination. `domain/EventService` applies archive/medication-note rules. `domain/StatisticsCalculator` owns duration clipping, calendar activity markers, period boundaries and trend aggregation. Neither domain nor model imports Android Views.
- `data/ProjectRepository`, `ParticipantRepository`, `SessionRepository` and `EventRepository` own all SQL, including list queries, CSV snapshots and notification reads. `SQLiteDatabaseService` retains connection/schema/migration ownership. Raw SQLite does not escape the composition root into UI.
- `design`, `time`, `settings` and `notifications` hold shared design tokens, participant colors, localized date presentation, preferences, export and foreground notification coordination. Shared components do not depend on feature screens.

The main direction is Activity → coordinator → feature/controller → domain → repository → SQLite. Simple display reads can use repositories directly. No Compose, Room, Hilt, new AndroidX lifecycle dependency, Gradle module or unused tab-service interface was introduced.

## Time picker design configuration

Edit the existing `app/src/main/assets/design_tokens.json` to change picker appearance:

- `colors.light` / `colors.dark`: `timeNight`, `timeDawn`, `timeMorning`, `timeDay`, `timeAfternoon`, `timeSunset`, `timeTwilight`, plus track/thumb outlines and shared text/surface colors.
- `dayTimeScale.gradientStops`: ordered minute/color-token pairs, starting at minute 0 and ending at 1440. The default anchors are night 00:00–05:00, dawn 06:00, morning 07:00, noon 12:00, afternoon 17:00, sunset 18:00, twilight 19:00 and night 20:00–24:00. Identical midnight colors make the day cyclic. These are deliberately approximate annual-average cues rather than sunrise forecasts.
- `dayTimeScale.labelIntervalHours`: spacing of major tick labels (default 6); hourly minor ticks retain the 24-hour scale.
- `dimensions`: shared values and `timePicker*`, `timeField*`, `timeScale*` sizes, spacing, fonts, border thicknesses and touch dimensions.
- `proportions`: disabled opacity, arrow geometry and major/minor tick opacity.

Bundled token changes take effect in the next APK. They do not change persisted timestamps, selection rules, theme preferences or participant colors. Invalid gradient positions, missing colors or out-of-range proportions fail explicitly instead of silently using another palette.

## State and lifecycle

Timer snapshots contain participant identity/colors, persisted session intervals, archive state and recent activity. The one-second callback changes existing labels/rings/strips; it never reconstructs the view hierarchy. Active-card backgrounds retain attachment/detachment and pause/resume animation handling.

History and statistics own their selection state. The coordinator serializes those values, current navigation and pending CSV project into Android `Bundle` state and restores them after recreation. These synchronous native screens use explicit controllers/snapshots and saved instance state; they do not need an observable asynchronous state pipeline. Dialog-local drafts retain the existing lifecycle behavior.

Notifications synchronize after successful session changes and at host startup, and read all persisted active sessions. Rendering and tab changes do not control foreground service lifetime. Timestamps remain epoch milliseconds; live durations use current time minus stored boundaries.

## Persistence safeguards

- The schema creation and migration bodies are byte-identical to branch head `8430555` from the start of this session. No database rewrite, table recreation, orphan cleanup, schema bump or signing change is performed.
- Session interval validation and writes share a transaction. Timer toggles use the same validated insert/update path. Completed-session deletion removes its dependent events in that transaction; active sessions cannot be deleted this way.
- Event association/bounds validation and its write share a transaction. Updating session boundaries cannot strand an event outside the session.
- Participant removal is rejected when sessions **or existing events** reference that participant. Project archival retains its active-timer guard and transaction.
- The partial unique index still prevents two active sessions for a participant. Queries retain project scoping; persisted participant IDs and colors remain stable.
- CSV keeps its BOM, semicolon separators, escaping, columns, machine-readable timestamp format and session-only content. It is not a database backup or event export.

## Verification and limits

Run `gradle :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest --no-daemon` with Gradle 8.9 and Android SDK 35. `:app:compileDebugAndroidTestKotlin` additionally compiles existing device tests without starting an emulator.

The fast JVM suite checks interval/event boundaries, simultaneous participants, running totals, local midnight, 23/25-hour Warsaw DST days, picker synchronization/precision/bounds and architectural dependency rules. Architecture tests reject SQLite in UI, Android/feature imports in domain/models, Activity imports in presentation and package cycles below the app composition root.

Local verification also compared every migration body with `8430555` and exercised fresh schema creation plus active-session uniqueness in host SQLite. This is not an Android upgrade/device test. The latest commit-specific PR `verify` job must pass before the change is considered verified; its URL and checked SHA belong in the PR description. Emulator jobs remain opt-in; when the user requests Android testing, follow [TESTING.md](TESTING.md) and report that separate run's exact SHA and results.

Manual device checks remain relevant: parallel timers and rapid taps, screen-off/process restart, both themes/languages, sticky calendars, expanding action trays, archived restrictions, existing event edits/additions, participant colors, CSV document-picker return and updates over an existing signed installation. No emulator or physical-device result is claimed by compilation.

Pre-existing product limitations remain: no accounts/sync, standalone events, event CSV or full database backup. Some existing labels remain hard-coded Polish; the refactor preserves them rather than redesigning UI. Existing-event timestamp selection is present in the actual baseline editor and remains constrained by session bounds.
