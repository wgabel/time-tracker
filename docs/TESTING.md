# Test execution

Use Gradle 8.9, Java 17 and Android SDK 35.

## JVM and build checks

```sh
gradle :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest --no-daemon
```

The JVM suite covers session/event interval rules, duration clipping, period boundaries, trend aggregation, DST and architecture dependencies. Daily trend tests verify 23/25 chronological hourly buckets at Warsaw clock changes, including repeated/skipped local-hour labels; future hours have zero duration. `DateTimeSelectionTest` checks the shared picker state: coarse five-minute selection, precise minute/hour adjustments, carry, fixed calendar date, inclusive timestamp bounds, untouched milliseconds, seconds, and missing/repeated DST times.

## Android SQLite and UI checks

```sh
gradle :app:assembleDebugAndroidTest --no-daemon
gradle :app:connectedDebugAndroidTest --no-daemon
```

The second command requires a running Android device/emulator. Building the test APK alone is not an executed device test.

- `RepositorySqliteTest` opens the production `SQLiteDatabaseService`, seeds isolated projects/participants and exercises entity repositories, domain services and timer snapshots. It checks atomic rollback with an injected SQLite delete failure, cross-project protection, archive restrictions, rapid timer toggles, event association, participant deletion and notification-change callbacks.
- `DatabaseMigrationTest` opens real fresh and legacy v1–v6 database files through the production helper. It verifies sessions, active state, event text/associations, participant IDs/colors, archive/icon state, uniqueness and integrity after upgrading and reopening.
- `IsolatedDatabaseContext` redirects helper files and migration preferences into disposable per-test storage. Repository/migration tests do not open or delete the app's actual `sessions.db`.
- `MainActivityUiTest` seeds its emulator app with the production helper. It covers timers, event forms, history editing, tab changes, background/return, notification navigation, Activity restart and saved-state recreation. Its fixture closes the shared application container before replacing its test database.
- Its focused time-picker checks use real swipes on hour/minute fields and the day rail, verify bidirectional values and hour carry, preserve seconds/milliseconds on save, reject an out-of-session second, and verify dismissing does not save. Light/dark screenshots are collected into the opt-in workflow's Android test reports.

The existing GitHub Actions workflow can be dispatched on the PR branch with `run_emulator_tests=true`. It runs SQLite/UI tests on Android 13. This remains opt-in, rather than enabling emulator runs for every PR. Inspect the run's exact head SHA, job conclusions and `android-integration-test-reports` artifact. After fixes, rerun against the new commit.

Before merging, require the latest PR `verify` job and the requested Android integration run to pass. After merging, verify the separate `main` signed release run and APK artifact. A PR build does not validate the merged release, and emulator results do not claim a physical-device test.
