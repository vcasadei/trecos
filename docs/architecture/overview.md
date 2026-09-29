# Architecture overview

Trecos is a single native Android app (Kotlin, Jetpack Compose, minimum
Android 9 / API 28) with no server. All data lives in a local database on the
device; Google Drive is the only, optional, remote. The full design, with
alternatives considered, is `openspec/changes/trecos-v1/design.md` (D1-D22).

## Modules

| Module | Purpose |
|---|---|
| `app` | The application (`app.trecos`), organised by feature package |
| `baselineprofile` | Generates the Baseline Profile and runs Macrobenchmarks on an emulator |

## Main building blocks

| Block | Choice | Design |
|---|---|---|
| UI | Jetpack Compose, Material 3 as a base, custom bottom bar and theme tokens | D1, D10, D11 |
| Wiring | A plain `AppContainer` created in `Application`; no DI framework | D2 |
| Database | Room on the SQLCipher engine, UUID ids, money in minor units | D3, D4 |
| Values | Container totals folded in memory per house | D5 |
| Search | FTS4 with diacritics removed | D6 |
| Photos | `ImageDecoder` to 1920 px WebP plus 320 px thumbnails | D8 |
| QR | Google code scanner in, ZXing out, system print framework | D9 |
| Preferences | Preferences DataStore | D13 |
| Sync | Per-house snapshots on Drive REST, three-way per-field merge | D14 |
| Backup | `.zip` export/import in the sync format | D15 |
| Security | Optional app lock; optional encryption with the key in Drive | D16, D17 |

## Current state

Release 0.1 in progress: the Gradle project and an empty Compose activity.
This page grows with each release.
