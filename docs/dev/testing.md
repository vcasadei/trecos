# Testing

Every test except the benchmarks runs on the JVM, so the headless machine and
CI need no emulator for them. The emulator is used only for Baseline Profiles
and Macrobenchmark; set it up with [setup-headless-linux.md](setup-headless-linux.md).

## Test types

| Type | Where | Runs on | Command |
|---|---|---|---|
| Unit tests | `app/src/test` (plain JUnit) | JVM | `./gradlew testDebugUnitTest` |
| Robolectric tests (UI, resources, manifest) | `app/src/test` (`@RunWith(RobolectricTestRunner::class)`) | JVM | `./gradlew testDebugUnitTest` |
| Screenshot tests (Roborazzi) | `app/src/test`, baselines in `app/src/test/screenshots/` | JVM | `./gradlew verifyRoborazziDebug` |
| Baseline Profile generation | `baselineprofile/` (`BaselineProfileGenerator`) | Emulator or rooted device | `./gradlew :app:generateBaselineProfile` |
| Startup benchmark | `baselineprofile/` (`StartupBenchmark`) | Emulator or device | `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` |

## Screenshot tests

| Task | What it does |
|---|---|
| `./gradlew recordRoborazziDebug` | Runs the tests and writes new baselines to `app/src/test/screenshots/` |
| `./gradlew verifyRoborazziDebug` | Fails if any screenshot differs from its baseline (CI runs this) |
| `./gradlew compareRoborazziDebug` | Writes comparison images to `app/build/outputs/roborazzi/` without failing |

Record again after an intended UI change, look at every changed PNG, and
commit the new baselines with the change.

Robolectric runs on Android SDK 35 (`app/src/test/resources/robolectric.properties`):
its Android 36 sandbox needs Java 21, and the project builds on JDK 17.

## Baseline Profile

1. Boot the emulator headless (setup guide, step 5) and check `adb devices` lists it.
2. Run `./gradlew :app:generateBaselineProfile` (about 2-3 minutes).
3. The profile is written to `app/src/release/generated/baselineProfiles/baseline-prof.txt`;
   commit it. Release builds package it, and `profileinstaller` applies it on install.

The generator cold-starts the app and visits Search, Settings and Home.
Regenerate it when a release changes startup or a main screen.

## Startup benchmark

`StartupBenchmark` cold-starts the app 10 times without a profile and with the
Baseline Profile, and reports `timeToInitialDisplayMs`. Emulator numbers are
only for comparing builds; the release gate (median of 1.5 s or less) is
measured on the 2 GB Android 9 reference phone.

## Release checks

| Check | Command |
|---|---|
| Debug and verbose logs are stripped from release | `./gradlew assembleRelease && scripts/check-release-logs.sh` |
| Backup excludes the database and key files | `BackupRulesTest` in `./gradlew testDebugUnitTest` |
