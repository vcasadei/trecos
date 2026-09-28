# Design

## Context

See `proposal.md` for motivation and scope. The repository has no application
code yet, only OpenSpec and a README, so this design also establishes the
project's conventions. Constraints that shape every decision:

- Native Android, minimum **Android 9 (API 28)**, must run well on **2 GB RAM**
  phones; cold start of 1-1.5 s on older devices is acceptable.
- **Offline-first**: every feature works without internet; Google Drive is the
  only (optional) remote, and there is **no Trecos server**.
- One **Google Play** build that may use Google Play services.
- Development happens on a **headless x86 Ubuntu** machine; UI must be
  verifiable without a physical screen.
- License: **PolyForm Noncommercial 1.0.0** for Trecos's own code.

## Goals / Non-Goals

**Goals:**
- A small, fast app: minimal dependencies, no background work except sync.
- A data model that already supports sync and future house sharing, so no
  reshaping is needed after 1.0.
- Every screen verifiable by automated screenshot tests on the headless machine.

**Non-Goals:**
- Any server, account system, analytics or crash-reporting SDK.
- Tablet-specific layouts (a later grid view), OCR, house sharing, attribute
  extraction, photo search and multiple users on one phone (post-1.0 roadmap).
- A Google-free (F-Droid) build.

## Decisions

### D1. Stack: Kotlin + Jetpack Compose, one app module
Kotlin with Jetpack Compose (Material 3 as a base, heavily themed), Navigation
Compose, and a single `app` module organised by feature packages
(`places`, `items`, `search`, `sync`...). A second module, `baselineprofile`,
exists only because the Baseline Profile plugin requires it.
- *Alternatives*: Android Views (lighter on very old devices, but slower to
  build and harder to screenshot-test); multi-module per feature (faster
  incremental builds, but premature for a single developer).
- Compose's cost on 2 GB devices is handled with Baseline Profiles (D20) and
  R8 full mode.

### D2. No dependency-injection framework
A plain `AppContainer` object created in `Application` holds the database,
file stores and sync engine, and is passed to ViewModel factories.
- *Alternatives*: Hilt or Koin (both new dependencies with build-time or
  runtime cost, for a graph of roughly ten objects).
- There are no repository interfaces with a single implementation. ViewModels
  call DAOs directly; a class that wraps DAOs exists only where it combines
  sources, for example `PhotoStore` (files plus database rows).

### D3. Database: Room on the SQLCipher engine, always
Room is the ORM. It always runs on SQLCipher's SQLite build, **without a key
when encryption is off**. Turning encryption on or off re-encrypts the file
with `sqlcipher_export` (D16).
- *Why always SQLCipher*: one SQLite engine means the same full-text search
  and collation behaviour whether or not encryption is on, and turning
  encryption on does not swap the storage engine.
- *Cost*: about 3 MB per CPU architecture (Play splits per architecture).
- *Alternative*: the platform SQLite for unencrypted users and SQLCipher only
  when encrypted. Smaller for most users, but two engines to test, and the
  switch becomes an engine migration.

### D4. Data model and identity
- Entities: `House`, `Container` (`houseId`, `parentId` nullable = top level
  of the house), `Item` (`houseId`, `containerId`), `Photo` (owner type and
  owner id, `sha256`, `position`), `Category` (built-in rows have a stable
  `key` such as `cables.usb_c` and `houseId = null`; custom rows have a
  `houseId`), `ItemCategory` (ordered, position 0 = main), `Tag`/`ItemTag`
  (per house), `FieldDef`/`FieldValue` (custom fields), `TrashEntry`, and
  sync metadata tables.
- **Every synced row** has a random UUID primary key (TEXT), `houseId`,
  `createdAt`, `updatedAt` and `deletedAt`. This supports sync (D14) and
  future sharing, where a house is the unit shared.
- `qrCode` is unique **per house**: a partial unique index on
  `(houseId, qrCode)` where the code is not null.
- **Money** is stored as `Long` minor units (for example cents), using the
  display currency's fraction digits. **Quantity** is a whole number (`Int`).
- Built-in categories are seeded from a bundled asset and never synced; they
  are referenced by `key`, so every device resolves them identically and
  translates their names through string resources.

### D5. Container values are computed in memory, not cached
To show values, the app loads one house's containers (`id`, `parentId`,
`override`) and per-container item totals (`SUM(quantity * unitPrice)`
grouped by container), then folds the tree bottom-up in Kotlin: the value is
the override if set, otherwise its items plus its children. This runs in
O(n), is fast for tens of thousands of rows, and needs no cache to invalidate.
- *Alternative*: a recursive SQL query or stored aggregates. Overrides make a
  recursive query awkward; stored aggregates add write-time bookkeeping and
  sync conflicts on derived data.

### D6. Search: FTS4 with diacritics removed
An FTS4 table (Room `@Fts4`, tokenizer `unicode61` with
`remove_diacritics=1`) indexes two columns: `name` (the item or container name
plus brand, model, serial and QR code) and `description`. Queries are
normalised (lowercase, diacritics removed) and every term gets a prefix `*`.
Filters (house, categories including subcategories, tags, container subtree)
are SQL joins on the FTS results. Input is debounced by about 150 ms.
- *Alternatives*: FTS5 (not guaranteed across SQLite builds on API 28);
  `LIKE '%x%'` (no ranking, full scans, no diacritics handling).

### D7. Category suggestions: dictionary plus learned counts
A bundled asset maps each built-in category key to English and Portuguese
keywords and synonyms (`usb-c`, `type-c`, `tipo c`...). A per-house table
`TokenCategoryCount(token, categoryId, count)` is updated whenever the user
saves an item's categories. While the user types, name and description are
tokenised and normalised, candidates are scored (dictionary hit plus learned
weight), and the top 3 not yet assigned are shown as chips. Nothing is ever
added automatically. This takes microseconds, uses no model, and works fully
offline.

### D8. Photos: decode once, store two files
With `ImageDecoder` (API 28), the app decodes directly to at most 1920 px on
the long edge. It applies EXIF orientation during the decode, then re-encodes
as WebP at quality 80, which drops all metadata, including GPS. A 320 px
square thumbnail is written at the same time. Files are stored in app-private
storage and named by SHA-256, so identical photos are stored once. The
original is never kept.
- Full-size files sync (D14); thumbnails never do and are regenerated if missing.
- Coil loads both, using thumbnails in every list.
- Input comes from the system camera (`ACTION_IMAGE_CAPTURE` into a
  `FileProvider` URI) or the Android Photo Picker (`PickMultipleVisualMedia`,
  maximum 3). No camera or storage permission is requested.

### D9. QR codes: Google scanner in, ZXing out
- **Scanning** uses the Google code scanner (Play services), which needs no
  camera permission and adds almost no size to the app. The payload is used
  as a raw string, so codes made by other programs work.
- **Generation** uses ZXing core to render a bitmap for display, sharing and
  printing.
- **Printing** uses the system print framework through `androidx.print`
  `PrintHelper` (approved 2026-09-28). The alternative was the framework
  `PrintManager` with a hand-written `PrintDocumentAdapter`, which needs no
  dependency but more code.

### D10. Navigation bar and theming are custom
The bottom bar (Search | Home | Settings) is one custom composable that draws
the floating rounded bar and the raised circle as a single `Path`. The
circle's x-position animates over about 200 ms and snaps when Android's
"Remove animations" setting is on. There are two themes: White (off-white)
and Dark (pure black), plus a Follow-system option. Colours come from our own
tokens. Material You dynamic colour is disabled.
- The fixed pastel palette of about 12 colours is defined as token pairs
  (a light and a dark variant, plus a deeper "band" variant for houses).
  Screenshot tests assert every pairing.
- The house colour band is drawn behind the status bar using edge-to-edge
  insets.

### D11. Text safety is built into shared components
All text in lists, bars and breadcrumbs goes through shared components that
set `maxLines` and ellipsis. Long unbroken words wrap mid-word. The breadcrumb
component measures its segments with `TextMeasurer` and collapses **middle**
segments into a tappable "…". Screenshot fixtures include 200-character
names, unbroken serial numbers, 8-level paths and Portuguese strings.

### D12. Languages and currency
App text lives in `strings.xml` for English and Portuguese (Brazil). Per-app
language uses `AppCompatDelegate.setApplicationLocales`, which covers
Android 9 to 12 (on 13 and later the system per-app setting works too).
Number formatting follows the app language. The currency symbol comes from
the currency setting, defaulting to the phone's region.

### D13. Preferences and small state in DataStore
Settings, rating-prompt counters, remembered filters, the last-used house and
the chosen image source live in Preferences DataStore. Nothing here syncs.

### D14. Sync: per-house snapshots in a visible Drive folder
- **Layout** in `My Drive/Trecos/`: `houses/<houseId>/commits/<id>.jsonl.gz`
  holds a full snapshot, one JSON line per row, sorted by table and id.
  `houses/<houseId>/refs/<deviceId>.json` holds each device's latest commit,
  and **only that device writes it**, so there are no write races.
  `houses/<houseId>/objects/<sha256>.webp` holds photos.
- **Commit** records: id, parent ids, timestamp, device id and name, Google
  user, change summary and format version.
- **Merge**: fetch the other devices' refs, then do a three-way merge
  **per field** between BASE (the last commit this device merged), LOCAL and
  REMOTE. A field changed on only one side takes that side. The same field
  changed on both sides, or an edit on one side against a delete on the
  other, becomes a **conflict** for the conflict screen. Trash state merges
  like any other field. With more than two devices, the merge folds one ref
  at a time.
- **Transport**: Drive REST v3 over `HttpsURLConnection` with
  kotlinx.serialization. The Google Drive client library is avoided (it is
  large and pulls in a large dependency tree). Scopes: `drive.file` (only
  files Trecos created) and `drive.appdata` (the hidden key file, D16). Auth
  uses Google Identity `AuthorizationClient`.
- **Schedule**: a WorkManager periodic job (daily, 5, 15 or 30 days, or never)
  requiring network. Photo uploads require an unmetered network when
  "Photos only on Wi-Fi" is on. "Sync now" enqueues an expedited job.
- **Retention**: each device keeps its last 30 commits on Drive. Objects no
  commit references are removed during sync.
- The merge engine is plain Kotlin with no Android types, so it can be tested
  exhaustively as JVM unit tests.

### D15. Backup files share the sync format
Export writes a `.zip` containing `manifest.json` (format version, date,
houses), each house's snapshot (`snapshot.jsonl`) and `objects/`. It is
written through the Storage Access Framework (`ACTION_CREATE_DOCUMENT`).
Import reads the manifest, shows a preview, then either replaces all data or
adds the houses as new ones (with new ids). Exports are never encrypted; a
warning is shown when encryption is on.

### D16. Encryption key lifecycle
Turning encryption on requires a connected Google account. The app generates
a 256-bit key, wraps it with an Android Keystore AES key for local use, and
uploads it to Drive's hidden `appDataFolder` for recovery. It then re-encrypts
the database with `sqlcipher_export` into a new file and swaps it in
atomically (the old file is kept until the new one opens). Drive snapshots are
encrypted with AES-GCM (`javax.crypto`) using the same key. When restoring on
a new phone, the user signs in and the app fetches the key and the latest
commits. Turning encryption off reverses the export and deletes the key from
Drive. Photos are never encrypted.

### D17. App lock
`androidx.biometric` `BiometricPrompt` with a device-credential fallback,
which covers the gaps in Android 9 and 10 through `KeyguardManager`. It locks
when the app returns after a configurable background timeout. There is no
Trecos password. The profile (name, e-mail) is optional plain data.

### D18. Monetization and help
- **Tip jar**: Play Billing consumable products (`tip_small`, `tip_medium`,
  `tip_large`).
- **Rating**: the Rate button opens the Play listing (`market://details`). The
  single automatic prompt uses the In-App Review API once 14 days have passed
  since install, with 20 or more items and 5 or more sessions, at an idle
  moment, and only once ever.
- **Contact**: a `mailto:hello@trecos.app` intent with the subject type and
  the app and Android versions filled in.
- **FAQ**: sourced from `docs/user/{en,pt-BR}/faq.md` and bundled as string
  resources.
- **Open-source licenses**: generated from Gradle metadata by
  **AboutLibraries** (approved 2026-09-28). The alternative was Google's
  `oss-licenses-plugin`, which is proprietary and uses an older View-based UI.

### D19. Feature flags
There's no remote configuration (there is no server), so flags are build-time
`BuildConfig` booleans:
- `FEATURE_DRIVE_SYNC`: default `false`, turned on in 0.11 for internal
  testing. Removal: at 1.0, after 30 days on the closed track without data-loss
  reports.
- `FEATURE_ENCRYPTION`: default `false`, turned on in 0.12. Removal: at 1.0,
  after on/off round trips pass on a physical Android 9 phone and on a current
  Android phone.

### D20. Testing, performance and CI
- **Tests**: JVM unit tests (merge engine, value folding, suggestion scoring,
  money), Robolectric plus Roborazzi screenshot tests (every screen, both
  themes, both languages, the stress fixtures, the palette pairings), and Room
  migration tests.
- **Performance**: a Macrobenchmark module generates the Baseline Profile and
  measures cold start and list scrolling. It runs on an emulator with KVM,
  which needs the user in the `kvm` group on the headless machine.
- **CI**: GitHub Actions for build, lint, unit tests and screenshot
  verification on every push; a release workflow signs with an upload key
  stored in GitHub Actions secrets, with Play App Signing on Google's side.

### D21. Dependencies (approved at proposal time unless marked)
AndroidX (Compose, Material 3, Navigation, WorkManager, DataStore, AppCompat,
Biometric, Activity, Room, Benchmark/ProfileInstaller), SQLCipher, Coil,
kotlinx.serialization, ZXing core, the Google code scanner, Google Identity,
Play Billing, In-App Review, Material Symbols, and JUnit, Robolectric and
Roborazzi for tests. AboutLibraries (D18) and `androidx.print` (D9) were
approved after the proposal, on 2026-09-28. Deliberately avoided: Hilt/Koin (D2), OkHttp and
Retrofit, the Google Drive client library (D14), and Firebase.

## Security & Observability

- **Secrets**: the app has no API secrets. The Google OAuth client is
  identified by package name and signing certificate. The upload signing key
  and its passwords exist only in GitHub Actions secrets and the developer's
  local keystore (git-ignored); none are in the repository, tests or fixtures.
- **Encryption key**: generated on the device; held wrapped by the Android
  Keystore and in Drive `appDataFolder` (D16); never logged or exported.
- **Personal data in logs**: there is no telemetry. Release builds strip
  debug and verbose logging with R8. Remaining log calls never include item,
  container or house names, descriptions, addresses, e-mails or serial
  numbers, only ids and counts.
- **Correlation and tracing**: not applicable, because there are no services
  or queues. Sync operations log the commit id and device id so a sync can be
  traced in the local log.
- **Backups**: Android Auto Backup is disabled for the database and key
  material (`dataExtractionRules`), because a restored Keystore-wrapped key
  cannot be unwrapped. Drive sync and export are the supported backups.

## Migration Plan

The database schema is new in 0.2 and evolves with every 0.x release that
people will have installed from the internal and closed test tracks.

- **Versioned Room migrations**: each schema change bumps the Room version
  with an explicit `Migration` (or `AutoMigration`), checked by
  `MigrationTestHelper` against exported schema JSON committed to the repo.
- **Expand and contract**: a column or table rename is split across releases.
  One release adds the new shape and writes both; a later release stops
  reading the old shape and drops it. That keeps devices on adjacent versions
  syncing the same house compatible.
- **Sync format version**: every commit and export carries `formatVersion`. An
  app refuses to merge a newer format than it understands and asks the user to
  update, rather than guessing. Readers accept the previous format for at least
  one release.
- **Rollback ("down")**: Android does not allow installing an older version
  over a newer one, so rollback is data-level. Before running any migration,
  the app copies the database file to `pre-migration-v<N>.db` and keeps it
  until the next successful launch. If the migration fails, the app restores
  the copy and reports the failure. A down path is tested for every migration
  by restoring that copy in the migration test suite.
- **Feature flags** (D19) keep sync and encryption off until their releases
  are validated.

## Risks / Trade-offs

- [Compose performance on 2 GB Android 9 phones] → Baseline Profiles, R8
  full mode, thumbnails-only lists, stable list keys; measured with
  Macrobenchmark before each release.
- [Sync merge bugs could lose data] → the JVM merge engine has exhaustive
  unit tests, the build flag stays off until 0.11, commits are retained on
  Drive, and export is available before sync ships (0.10).
- [SQLCipher adds about 3 MB per architecture] → accepted for one consistent
  engine (D3).
- [Play services unavailable (Huawei, custom ROMs)] → accepted (#2): scanning,
  sign-in, sync, tips and review are unavailable; everything else works.
- [Play policy on external donation links] → no external payment links in the
  app; tips go through Play Billing only.
- [A lost Google account means losing the encryption key] → accepted by the
  user; documented in the FAQ and `SECURITY.md`.
- [Drive quota or API errors] → sync retries with backoff, never blocks the UI,
  and shows its status in Settings > Sync.
- [The KVM group is not set up on the dev machine] → screenshot tests run
  without an emulator; only benchmarks need KVM.

## Open Questions

- The exact pastel palette values, to be tuned against contrast tests in 0.1.
- The full keyword lists for category suggestions (the structure is fixed in D7).
- Play's current closed-testing requirement (number of testers and days),
  to check before 0.7.
