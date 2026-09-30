# Build

Set up the machine first with [setup-headless-linux.md](setup-headless-linux.md).
Run every command from the repository root.

## Toolchain

| Component | Version | Where it is set |
|---|---|---|
| JDK | 17 | the machine (`java -version`); CI uses Temurin 17 |
| Gradle | 9.8.0 (wrapper, checksum-pinned) | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.4.1, with built-in Kotlin | `gradle/libs.versions.toml` |
| Kotlin (Compose compiler plugin) | 2.4.20 | `gradle/libs.versions.toml` |
| `compileSdk` | 37 | `app/build.gradle.kts` (current AndroidX requires it) |
| `targetSdk` | 36 | `app/build.gradle.kts` (Google Play's requirement) |
| `minSdk` | 28 (Android 9) | `app/build.gradle.kts` |

Dependencies live only in the version catalog, `gradle/libs.versions.toml`.
A new library needs the maintainer's approval first (see `CONTRIBUTING.md`).

## Commands

| Goal | Command | Output |
|---|---|---|
| Debug APK | `./gradlew assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK (unsigned until signing is set up in 0.2) | `./gradlew assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Lint | `./gradlew lintDebug` | `app/build/reports/lint-results-debug.html` |
| Unit, Robolectric and screenshot tests | `./gradlew testDebugUnitTest` | `app/build/reports/tests/testDebugUnitTest/` |
| Screenshot verification | `./gradlew verifyRoborazziDebug` | fails on any change from `app/src/test/screenshots/` |
| Coverage threshold | `./gradlew jacocoCoverageVerification` | fails below 80% of lines in non-UI code |
| No debug logs in release | `scripts/check-release-logs.sh` (after `assembleRelease`) | fails on any `Log.d` or `Log.v` call |

Testing in more depth, including screenshots, Baseline Profiles and
benchmarks: [testing.md](testing.md).

## Build settings

| Setting | Value | Why |
|---|---|---|
| Kotlin warnings | treated as errors | the "strict type checking" quality gate |
| Release shrinking | R8 full mode, resource shrinking | size and startup on 2 GB phones (design D1) |
| Release logging | `Log.d` and `Log.v` removed by R8 (`app/proguard-rules.pro`) | no personal data in logs |
| Android backup | database and `files/keys/` excluded (`res/xml/`) | a restored Keystore-wrapped key can't be unwrapped |
| Locales | `en` (default) and `pt-BR` in `res/xml/locales_config.xml`; Portuguese strings in `values-pt` | per-app language on every Android version; pt-PT phones get pt-BR |
| Configuration cache | on (`gradle.properties`) | faster repeat builds |

## CI

`.github/workflows/ci.yml` runs on every push and pull request, on
`ubuntu-24.04`: build, lint, unit tests, coverage, screenshot verification,
release build and the release log check. `.github/workflows/secret-scan.yml`
scans the whole history with gitleaks. `.github/workflows/dependency-graph.yml`
submits the app's runtime dependencies (every `*RuntimeClasspath`) to GitHub on
every push to `master`, so Dependabot alerts cover every shipped library,
transitive ones included. Gradle plugin classpaths are not submitted: they
never ship, and Dependabot can't update them.
