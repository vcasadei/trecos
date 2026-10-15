# Design

## Context

See proposal.md for the why. The facts that shape the approach:

- One `:app` module in feature packages, manual wiring through `AppContainer`, ViewModels built with `appViewModel { }`, Navigation Compose with string routes (trecos-v1 D1, D2). The owner chose to keep this (2026-10-14).
- Room on SQLCipher at schema version 6, AutoMigrations behind `MigrationGuard`; search is an FTS4 table kept current by triggers (`data/SearchIndex.kt`, ADR 0023).
- `CategorySuggester` scores typed text against keyword assets and per-house learned counts and never assigns (ADR 0019).
- Photos are content-addressed WebP files (`files/photos/<sha256>.webp`), up to 3 per owner, from the camera app or the Photo Picker, with no permissions (ADR 0012, 0022).
- QR scanning uses Google's code scanner from Play services.
- JaCoCo gates 80% of lines with `app/trecos/ui/**` excluded; there is no HTML report task.
- Release gate: cold start within 50% of the reference-emulator baseline, with a warning above 15% (ADR 0028, 0029). Release arm64 APK at 0.14.0: 7.7 MB; universal 13.1 MB.

## Goals / Non-Goals

**Goals:**
- Ship the four features, the design pass and the quality bars as a sequence of small releases, each usable on its own, before 1.0.
- Keep every existing guarantee: offline, 2 GB phones, the cold-start gate, sync compatibility, both languages.

**Non-Goals:**
- Hilt, multi-module or a Clean Architecture use-case layer.
- House sharing and multiple users (still after 1.0).
- Matching a photo to one specific item (embeddings); search matches the kind of thing.
- AI text generation (Gemini Nano) for attributes.

## Decisions

### D1. Release sequence

| Release | Content |
|---|---|
| 0.15.0 | What is on master now (sync fixes, device tests). The Play closed test starts with it (trecos-v1 8.7, 8.8). |
| 0.16 | Foundations: project skills, design tokens and components, coverage report with UI counted, trial build that measures the ML libraries and sets the size budget. |
| 0.17 | Self-contained scanning and phones without Play services. |
| 0.18 | Attribute suggestions (rules only, no new library). |
| 0.19 | Item recognition (labels and text) prefilling the form. |
| 0.20 | Photo labels in search and search from a photo. |
| 0.21 | Wide screens and the grid view. |
| 0.22 | Design pass completed on the remaining screens; coverage at 90%. |
| 1.0 | trecos-v1 group 14 (flags removed, performance pass, listing). |

Attribute suggestions come before recognition because recognition reuses their rules on recognized text. Each release goes to the closed test track, so testers see every feature.

*Alternative:* one big release before 1.0. Rejected: a single large change is harder to test and review, and testers would see nothing new for weeks.

### D2. Architecture: adopt patterns, keep the structure

New and rewritten screens follow these patterns. Existing screens adopt them only when a release touches them; there is no big-bang refactor.

- A screen's state is one immutable `UiState` (a sealed interface when the screen has distinct loading, content and empty states), exposed as `StateFlow` with `stateIn(WhileSubscribed(5_000))`.
- A `Route` composable connects to the ViewModel; a stateless `Screen` composable takes state and callbacks, so it can be previewed, screenshot-tested and covered under Robolectric.
- One-off events such as "saved" go through callbacks or a state field the screen consumes, not a `Channel`.
- Repositories own data access for a feature; ViewModels don't call DAOs directly in new code.
- Tests use fakes over mocks, as today.

These are written as a project skill, `.claude/skills/trecos-architecture/SKILL.md`, which states explicitly: single module, manual DI through `AppContainer`, no Hilt.

*Alternatives:* adopt dpconde/claude-android-skill as is (Hilt and api/impl feature modules, an estimated 3–5 weeks of refactoring), or Hilt only. The owner chose patterns only.

### D3. Third-party skills

Skills are text that steers the agent, so they are reviewed and pinned like code:

- **rcosteira79/android-skills (MIT)** at a pinned commit: copy `compose`, `android-testing`, `kotlin-coroutines`, `kotlin-flows`, `android-data-layer`, `android-ux` and `android-debugging` into `.claude/skills/`, with a NOTICE naming the source, commit and license. Edit out Hilt and multi-module defaults.
- **ceorkm/mobile-app-ui-design** has no LICENSE file, so it is not copied. A new `trecos-design` skill rewrites its process (context, structure, visuals, emotion, polish) for Material 3: 48 dp targets, the M3 type scale, color roles and dynamic color, tonal elevation, the shape scale and window size classes. It credits the source.
- **lintapple** inspects web pages only. Its rules (contrast, minimum control size, text overflow, nested corner radii, equal digit widths) become a checklist in `trecos-design`, enforced by tests (D11).
- **amicro.dev** is a React library: inspiration only. Effects worth having are rebuilt with Compose animation APIs within the Motion requirement.
- **Drjacky/claude-android-ninja** and **dpconde/claude-android-skill** are not installed. Useful parts (offline-sync notes, UiState and Route patterns) are folded into the project skills.

No skill is installed with `npx skills add`, a plugin marketplace or an unpinned `git clone`. Updates are diffed against the reviewed copy.

### D4. On-device ML libraries (approved by the owner, 2026-10-14)

| Need | Choice | Alternatives considered |
|---|---|---|
| QR | ML Kit barcode scanning, bundled, QR format only | Google code scanner (needs Play services and a download); ZXing (smaller, less robust) |
| Packaging text | ML Kit text recognition v2, bundled Latin model | Play services model (needs a download); Tesseract (larger, less accurate) |
| What a photo shows | ML Kit image labeling, bundled base model | Image embeddings (match one specific item, not wanted); LiteRT with an open model (no metrics, but lower accuracy and own label set) |
| Scanner camera | CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`) | Camera2 directly (much more code) |

Bundled models work without Google Play services and with no network from the first launch. ML Kit sends Google usage metrics about itself; the owner accepted that and chose to disclose it (help-and-support "No tracking").

The ML Kit clients sit behind small app interfaces (`Recognizer`, `QrReader`) with fakes for tests. This is a real second use: Robolectric tests can't run the native models, so every screen test uses the fake.

### D5. Recognition pipeline

1. The photo is stored first (PhotoStore, as today), then a copy downscaled to 1024 px long edge is read.
2. Labels: keep those with confidence of at least 0.6, at most 5.
3. Text: blocks sorted by size times confidence. The name is the most prominent line of 3–60 characters that isn't only digits or a barcode; with no such line, the top label in the app's language.
4. Suggestions:
   - all text plus labels go to `CategorySuggester` (category chips) and to the attribute rules (D6);
   - labels become tag chips;
   - the remaining text is appended to an empty description.
5. The form marks prefilled values as suggested until the user edits or accepts them; nothing is saved before Save.

A new asset, `assets/image-labels.json`, maps each ML Kit label to English and Portuguese words and, where one fits, to a built-in category key, which gives the categories a boost. Recognition runs on a background dispatcher and is cancelled if the form closes.

### D6. Attribute rules

`places/AttributeRules.kt` is a pure function from text to `List<AttributeSuggestion(name, value, type, unit)>`, backed by `assets/attribute-rules.json`:

- known brands, with aliases ("rpi" for Raspberry Pi);
- unit patterns, with English and Portuguese names: GB/TB/MB → RAM or Storage depending on context words; mAh → Capacity; W, V, A; mm/cm/m/"/in/polegadas → Length or Size.

Learning reuses the house-scoped pattern of `token_category_count`: a new table `attribute_choice(houseId, ruleKey, fieldName)` records the field a user picked or renamed for a rule.

### D7. Data model (schema version 7)

- `photo_label(photoSha TEXT, label TEXT, confidence REAL, PRIMARY KEY(photoSha, label))`, keyed by content hash, so one photo used twice is labelled once. It is derived on each device and **not synced**, like thumbnails, so the sync format and its compatibility rules don't change.
- `photo_label_state(photoSha PRIMARY KEY, labelledAt)` records photos already processed, including those with no labels.
- `attribute_choice` (D6).
- The search FTS table gets a third column, `photos`, filled by triggers from the labels of an owner's photos in the app's language. FTS4 can't add a column, so the migration recreates the virtual table and its triggers, then rebuilds the index, the same way as `Migration5To6`.

### D8. Background labelling

A WorkManager job labels unlabelled photos in batches of 20, requires battery not low, and stops when cancelled. It is enqueued after the update and whenever photos are added outside the form, such as by sync or import. Newly identified photos are labelled immediately.

### D9. Phones without Google Play services

`AppContainer.playServices` is checked once with `GoogleApiAvailability`. Sync, tips and rating read it to show their explanation; everything else ignores it. The device tests add a cell on an AOSP image without Google APIs (`default` target) running the core flows.

### D10. Wide screens and the grid

- The `material3-window-size-class` library (part of Compose Material 3) supplies the width class. At 600 dp or more, `TrecosApp` swaps the floating bottom bar for a `NavigationRail`.
- `ListView` gains `Grid`, drawn with `LazyVerticalGrid(GridCells.Adaptive(160.dp))`, reusing the row's thumbnail and name.
- The stored preference keeps reading old values, since a new enum value is additive. A per-device flag records whether the user picked a view, so wide windows default to the grid until they choose.

### D11. Design pass and its checks

1. Tokens first: color roles, the type scale, shapes, an 8 dp spacing scale, and motion durations and easing, in `ui/theme`.
2. Then components (rows, tiles, chips, empty states, form fields), then screens, release by release.
3. Roborazzi screenshots are re-recorded per screen in both themes and both languages, at 360 dp and 840 dp wide.
4. Accessibility is enforced in tests: Compose UI tests check touch-target size and contrast with the accessibility checks of the Compose test library, under Robolectric where supported, otherwise in the device suite. `PaletteContrastTest`, which already enforces 4.5:1 on the palette, extends to every color-role pair in both themes.

### D12. Coverage to 90% with UI counted

1. Add a `jacocoTestReport` task (HTML and XML).
2. Remove the `app/trecos/ui/**` and `MainActivity` exclusions; keep only generated code excluded.
3. Measure the baseline, set the gate to it rounded down, and raise it each release (a ratchet), reaching 90% by 0.22.
4. Most of the gain comes from testing stateless `Screen` composables under Robolectric (D2). ViewModels are already reachable.
5. CI prints the totals in the job summary.

### D13. Size budget

- Task 2.1 builds a release with the four libraries and records the per-ABI and universal sizes in this design.
- The owner then sets the budget, and `scripts/check-apk-size.py` fails CI and releases above it.
- ML Kit's native libraries are per ABI, while models are shared assets, so the universal APK grows by less than the sum.

### D14. Performance

- No ML client is created at startup. They are built lazily on first use and released when the form closes.
- The cold-start gate (ADR 0028) is unchanged.
- An instrumented test on the reference emulator times recognition of three fixture photos against the 3-second requirement.
- `ScrollJankTest` adds the grid view.

## Security & Observability

- **New permission:** the camera, asked only on the first scan.
- **New data:** photo labels and recognized text are personal data under LGPD. They are stored only in the encrypted local database, never synced, and removed with their photo.
- **Telemetry:** ML Kit's usage metrics are Google's, disclosed in PRIVACY.md and the Play data safety form. The app logs no recognized text or labels.

## Migration Plan

- Schema version 6 to 7 is an additive migration: three new tables and the recreated FTS table, built in one transaction, with a migration test from v6 data that checks the search results.
- **Rollback:** a downgrade isn't supported by Room. As for every schema change, the release is held on the closed track until it has been checked there.
- Sync is untouched, since labels aren't synced.
- **Feature flag:** `FEATURE_RECOGNITION` (default on in debug, off in release) guards Identify and photo search until 0.20 passes its device tests. It is removed before 1.0, as in trecos-v1 14.7.

## Risks / Trade-offs

- **ML Kit labels are generic** (about 400 classes). Many household objects only get "Tool" or "Electronic device". → Packaging text comes first; labels add categories, tags and search words, and the user corrects everything.
- **Text recognition picks the wrong line as the name.** → It is only a suggestion, marked as such; the heuristic is tested on a fixture set of packaging photos.
- **APK size grows by several MB per ABI.** → The trial build measures it first; the owner sets the budget.
- **Memory on 2 GB phones.** → One client at a time, a downscaled input, released after use; a device test runs recognition on the 2 GB cell.
- **The camera permission changes the app's no-permission stance.** → It is asked only on scan, and refusing still leaves typing a code.
- **Coverage pressure produces shallow tests.** → Tests map to spec scenarios; reviews reject assertion-free tests.
- **1.0 moves later.** → The closed test runs in parallel, and each release is usable by testers.
- **Archive order.** → The deltas modify specs that live in `trecos-v1`, so trecos-v1 and device-test-suite are archived before this change.

## Open Questions

- The APK size budget, set from the trial build (D13).
