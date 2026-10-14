# Testing

Most tests run on the JVM, so the main CI job needs no emulator. The device
suite (instrumented tests and Maestro flows), Baseline Profiles and
Macrobenchmark run on emulators; see [device-tests.md](device-tests.md) and
[setup-headless-linux.md](setup-headless-linux.md).

## Test types

| Type | Where | Runs on | Command |
|---|---|---|---|
| Unit tests | `app/src/test` (plain JUnit) | JVM | `./gradlew testDebugUnitTest` |
| Robolectric tests (UI, resources, manifest) | `app/src/test` (`@RunWith(RobolectricTestRunner::class)`) | JVM | `./gradlew testDebugUnitTest` |
| Migration tests | `app/src/test/.../data/MigrationTest.kt`, `MigrationGuardTest.kt`, against `app/schemas/` | JVM | `./gradlew testDebugUnitTest` |
| Screenshot tests (Roborazzi) | `app/src/test`, baselines in `app/src/test/screenshots/` | JVM | `./gradlew verifyRoborazziDebug` |
| Baseline Profile generation | `baselineprofile/` (`BaselineProfileGenerator`) | Emulator or rooted device | `./gradlew :app:generateBaselineProfile` |
| Startup benchmark | `baselineprofile/` (`StartupBenchmark`) | Device (emulator with a flag, below) | `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` |
| Instrumented tests | `app/src/androidTest` (SQLCipher, encryption, backup, photos) | Emulator cells of the device suite | `./gradlew connectedDebugAndroidTest` |
| Device flows (Maestro) | `maestro/flows/`, coverage in `maestro/COVERAGE.md` | Emulator cells, Android 9 to 16 × 2 to 6 GB | `scripts/run-device-tests.py`, see [device-tests.md](device-tests.md) |
| Script tests | `scripts/test_*.py` | Python 3 | `python3 -m unittest discover -s scripts -p 'test_*.py'` |

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
Baseline Profile, and reports `timeToFullDisplayMs` (the Home list loaded and
reported drawn) and `timeToInitialDisplayMs` (the first frame) in
`baselineprofile/build/outputs/connected_android_test_additional_output/`. The
release gate uses only `timeToFullDisplayMs`.

Macrobenchmark refuses to run on an emulator unless told to, because emulator
numbers don't represent real phones. To compare builds on the headless
machine anyway:

```sh
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

`ScrollJankTest` flings the Home list of 1,000 items and reads the share of
janky frames from `dumpsys gfxinfo` while the app is open. It writes
`scroll-jank-benchmarkData.json` next to the startup results, in the same
shape, so `scripts/check-benchmark.py` reads both. Macrobenchmark's own frame
metrics fail on Android 9: the traced one misses an app that started before
tracing, and the gfxinfo one reads after the app is stopped.

On Android 9, Macrobenchmark also needs a frame after `reportFullyDrawn()`,
which Android 9 doesn't draw by itself; `MainActivity` requests one.

The release gate (median of 1.5 s or less, janky frames under 5%) was written
for a 2 GB Android 9 phone. Reference numbers:

| Device | Cold start, with profile | Cold start, without | Janky frames scrolling 1,000 items |
|---|---|---|---|
| API 36 emulator, release 0.1 | 894 ms | 939 ms | not measured |
| API 28 emulator, 2 GB, 2 cores, software GPU (2026-10-12) | 1,792 ms | 1,813 ms | 97.7% |

An emulator without a GPU draws every frame in software, so its jank share
says nothing about a phone. On the reference emulator the release gate
therefore checks only that the cold start is not more than 15% slower than the
recorded baseline. The absolute budgets are checked on a real phone in
Firebase Test Lab (docs/dev/release.md, ADR 0028).

## Coverage

| Rule | Value |
|---|---|
| Counter | Lines |
| Minimum | 80% |
| Measured | Non-UI code only: `app/trecos/ui/**`, `MainActivity`, generated Compose and resource classes are excluded, since screenshot tests cover the UI |
| Command | `./gradlew jacocoCoverageVerification` (runs the unit tests first) |
| Report data | `app/build/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec` |

The threshold may only be raised.

## Release checks

| Check | Command |
|---|---|
| Debug and verbose logs are stripped from release | `./gradlew assembleRelease && scripts/check-release-logs.sh` |
| Backup excludes the database and key files | `BackupRulesTest` in `./gradlew testDebugUnitTest` |
