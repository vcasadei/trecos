# Proposal

## Why

Home and maker inventories — cables, adapters, boards, tools, spares spread
across several houses, rooms and boxes — are hard to keep track of, and the
existing apps are online-first, subscription-based, ad-supported or closed.
Trecos is a free, source-available, offline-first native Android app (Android 9+)
that records what you own, where it is physically stored, and what it is worth,
with printable QR labels and optional sync to the user's own Google Drive.

## What Changes

This is a greenfield product. The change delivers **Trecos v1.0** through
thirteen incremental, independently testable releases (0.1 to 0.12, then 1.0);
see `tasks.md` for the grouping. There are no breaking changes: nothing exists yet.

- **Project foundation**: rename the product and repository from Cubby to
  **Trecos** (`github.com/vcasadei/trecos`, domain `trecos.app`, package
  `app.trecos`); license under **PolyForm Noncommercial 1.0.0** with a
  commercial-license path and a CLA; create the documentation structure
  (product, architecture, decisions, dev, bilingual user docs).
- **Places**: multiple **houses** (a special top-level entity with name,
  optional address, description, photos, icon and color); **containers** nested
  to any depth; recursive value totals with manual override; pastel colors
  with inheritance; tappable breadcrumbs.
- **Items**: name (only required field), whole-number quantity, decimal unit
  price, brand, model, serial number, QR code, description, tags, photos.
- **Categories and tags**: two-level built-in categories (~20 groups, ~95
  subcategories, translated, with icons); **multiple categories per item**;
  custom categories; free-form tags as the user's own system; offline
  **category suggestions** from name and description as tap-to-add chips.
- **Organize**: move, copy and duplicate (also between houses), multi-select
  bulk actions, a 30-day per-house **trash**, and a container-delete flow that
  lets the user choose what to keep and where to move it.
- **Photos**: up to 3 per item, container or house; system camera or photo
  picker; stored at 1080p WebP with local thumbnails; location metadata removed.
- **Search**: a Search tab with accent-insensitive partial matching, scope,
  filters (house, category, tags), sorting, and "search in this container".
- **QR codes**: per-house unique codes that default to the name and survive
  renames; generate, share and print labels; scan (including codes made by
  other programs) to find or create.
- **Custom fields**: user-defined fields per house or per item, with unit labels.
- **Settings and UI**: English and Portuguese (Brazil), display currency,
  start screen, list views (condensed and detailed), add flow, image source;
  White and OLED-black themes; a three-tab bottom bar (Search | Home | Settings).
- **Security**: optional app lock using the phone's own biometrics or PIN; a
  fully optional local profile; optional database encryption with the key kept
  in the user's Google Drive for recovery.
- **Backup and sync**: local export/import (.zip); Google Drive sync in a
  visible folder with git-like per-field three-way merge, a conflict screen
  only for real conflicts, sync history, and a configurable schedule.
- **Help and support**: FAQ (10 questions), contact by e-mail, Play Store
  rating, an in-app tip jar, and an About screen with an open-source licenses
  list generated from the build. No ads, ever.

Out of scope for v1 (ordered roadmap after 1.0): OCR prefill, house sharing,
attribute extraction, grid view for large screens, photo search, and multiple
users on one phone.

## Capabilities

### New Capabilities

- `app-shell`: navigation, themes, animations, text truncation, languages and performance targets.
- `places`: houses and nested containers, colors, icons, breadcrumbs and value totals.
- `items`: item fields, quantities, prices and the add/edit flow.
- `categories-and-tags`: built-in and custom two-level categories, multiple categories per item, tags, and suggestions.
- `organize`: move, copy, duplicate and multi-select, within and between houses.
- `trash`: deletion with confirmation, undo, 30-day retention, restore, and the container-delete flow.
- `photos`: capture, picking, storage size, thumbnails, main photo and limits.
- `search`: text search, scope, filters, sorting and container-scoped search.
- `qr-codes`: QR identity, uniqueness, generation, printing, scanning and lookup.
- `custom-fields`: user-defined fields and unit labels.
- `settings`: user preferences, list views and first-run choices.
- `app-lock`: the optional app lock and the optional local profile.
- `encryption`: optional database encryption and key recovery through Google Drive.
- `backup`: local export and import.
- `sync`: Google Drive sync, merging, conflicts, history and scheduling.
- `help-and-support`: FAQ, contact, rating, tip jar, About and open-source licenses.

### Modified Capabilities

None. The project has no existing specs.

## Impact

- **Code**: a new Android application (Kotlin, minimum Android 9 / API 28) and a
  new local database; no server, no public API.
- **Repository**: renamed on GitHub; new license, contributor agreement and
  documentation tree; OpenSpec specs created for every capability above.
- **Database schema**: new. Every row carries a UUID, timestamps, a deletion
  marker and its house, so sync and future house sharing need no reshaping.
- **Personal data (LGPD)**: optional name and e-mail; the Google account used
  for sync; house addresses; photos; item values and serial numbers. All data
  stays on the device and, only if the user enables it, in the user's own
  Google Drive. Photo location metadata is removed. No location is collected.
  A privacy policy (`PRIVACY.md`) is required for the Play Store.
- **External services**: Google Drive (optional sync), Google Play services
  (QR scanning, sign-in, billing, reviews), Google Play Store distribution.
  No analytics or crash-reporting SDK: crashes are read from Play Console only.
- **Licensing**: PolyForm Noncommercial covers Trecos's own code only; bundled
  third-party components keep their own licenses. All open-source
  dependencies are permissive (Apache 2.0, MIT, BSD), with no copyleft. The
  Google Play libraries are proprietary but freely distributable. Attribution
  is met by the in-app licenses screen.
- **New third-party dependencies (all approved by the user on 2026-09-28)**:
  - Jetpack Compose, Material 3, Navigation, WorkManager, DataStore, AppCompat
    (per-app language), Biometric, Activity (photo picker) — AndroidX, Google.
  - Room with SQLite full-text search — AndroidX, Google.
  - SQLCipher for Android — Zetetic, database encryption.
  - Coil — image loading and thumbnail caching.
  - kotlinx.serialization — JSON Lines for backup and sync.
  - ZXing core — QR code generation for labels.
  - Google code scanner (Play services) — QR scanning without camera permission.
  - Google Identity / Authorization and Google Drive REST API — sync.
  - Google Play Billing Library — tip jar.
  - Google Play In-App Review — the single rating prompt.
  - Material Symbols — bundled icons for categories, houses and containers.
  - AboutLibraries — the in-app open-source licenses screen.
  - androidx.print — printing QR labels through the system print dialog.
  - Test and build only: JUnit, Robolectric, Roborazzi (screenshot tests),
    Baseline Profiles / Macrobenchmark.
