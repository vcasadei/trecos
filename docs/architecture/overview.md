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

## Packages

| Package | Contents | Since |
|---|---|---|
| `app.trecos` | `MainActivity`, the single activity | 0.1 |
| `app.trecos.ui.theme` | `ThemeMode`, colour tokens, the 12-colour `PaletteColor`, `Motion` tokens, `TrecosTheme` | 0.1 |
| `app.trecos.ui.language` | `AppLanguage`: English and Portuguese (Brazil), switched with AppCompat per-app locales | 0.1 |
| `app.trecos.ui.shell` | `TrecosApp` (navigation graph), `TrecosBottomBar`, `TrecosTopBar`, `TrecosTab` | 0.1 |
| `app.trecos.ui.text` | `SafeText` and the collapsing `Breadcrumb` | 0.1 |

## How the shell fits together

```
MainActivity (AppCompatActivity, edge to edge)
└── TrecosTheme            colour scheme + LocalDarkTheme + LocalMotion
    └── TrecosApp          NavHost: search | home (start) | settings
        ├── tab roots      TrecosTopBar without a back arrow
        └── TrecosBottomBar  selected tab follows the back stack
```

- **Navigation**: Home is the start destination. Switching tabs pops back to
  Home and saves each tab's state, so system back on the Search or Settings
  root returns to Home, and back on Home leaves the app.
- **Theme**: colours come only from `ColorTokens`; there is no dynamic colour.
  Dark is pure black. Palette tints and bands are contrast-tested (at least 4.5:1).
- **Motion**: `Motion.Standard` (150/200/250 ms) or `Motion.Instant` when the
  system animator scale is 0 ("Remove animations").
- **Languages**: strings in `res/values` (English, the default) and
  `res/values-pt`, which serves Portuguese (Brazil) to every Portuguese phone,
  Portugal included; the choice persists on Android 9-12 through AppCompat's
  `autoStoreLocales` service.

## Current state

Release 0.1 (foundation): the shell, themes, palette, languages, text safety,
motion, Baseline Profile generation and release hardening. There is no data
yet; places and items arrive in 0.2.
