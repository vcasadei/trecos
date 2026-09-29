# Changelog

All notable changes to Trecos are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-09-29

Foundation release. Not distributed: the first sideloadable APK is 0.2.

### Added

- Android project (`app.trecos`): Kotlin, Jetpack Compose, minimum Android 9, target Android 16.
- White (off-white) and Dark (pure black) themes with a Follow system option; no wallpaper colours.
- A 12-colour pastel palette with White, Dark and band variants, all contrast-tested at 4.5:1 or more.
- English and Portuguese (Brazil), following the device language, with an in-app switch that works on Android 9 and later.
- Floating bottom bar (Search, Home, Settings) with a raised circle that slides between tabs.
- Navigation with Home as the start: back from Search or Settings returns Home; back on Home leaves the app.
- Text that truncates with "…" and a location breadcrumb that collapses to its last two levels.
- Motion tokens (150-250 ms) that become instant with Android's "Remove animations".
- Baseline Profile generation and a cold-start benchmark.
- Release builds strip debug logs; Android backup excludes the database and key files.
- License (PolyForm Noncommercial 1.0.0), CLA, contribution, security and privacy documents, and the documentation tree.
- CI: build, lint, unit and screenshot tests, coverage threshold, release log check, secret scan.
