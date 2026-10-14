# Tasks

## 1. Plan and Play closed test (0.15.0)

- [ ] 1.1 Release 0.15.0 from master (sync fixes, device tests); verify the release workflow publishes the APKs and the device-test release tier passes
- [ ] 1.2 **(owner)** Create the Play developer account, app and closed test, and upload 0.15.0 (trecos-v1 8.7, 8.8, with the agent's scripts); verify the closed-test opt-in link installs the app
- [ ] 1.3 Write the tester invitation (Portuguese and English) and a "what to try" checklist in `docs/dev/release.md`; verify both are linked from the closed-test section
- [ ] 1.4 Update docs/product/roadmap.md, vision.md, ADR 0026 (superseded by a new ADR for this plan) and the trecos-v1 non-goals for the new order; verify `openspec validate --all` passes and the roadmap lists 0.16–0.22 before 1.0

## 2. Foundations (0.16)

- [ ] 2.1 Trial build: add the bundled ML Kit barcode, text and labeling libraries and CameraX to a scratch branch with a minimal call to each, build release APKs and record per-ABI and universal sizes in design.md D13; **(owner)** set the size budget from them
- [ ] 2.2 Add `scripts/check-apk-size.py` (standard library) with the budget and a self-test, run it in `ci.yml` and `release.yml`; verify a fake oversized APK fails the self-test
- [ ] 2.3 Copy the reviewed rcosteira79/android-skills skills at a pinned commit into `.claude/skills/` with a NOTICE (source, commit, MIT), editing out Hilt and multi-module defaults; verify each SKILL.md loads and names the project's DI and module rules
- [ ] 2.4 Write `.claude/skills/trecos-architecture/SKILL.md` (D2) and `.claude/skills/trecos-design/SKILL.md` (D3, with the Material 3 rules and the lintapple-derived checklist, crediting sources); verify both are linked from `docs/architecture/overview.md` and a new `docs/dev/skills.md` that lists every project skill, its source and pinned commit
- [ ] 2.5 Add the `jacocoTestReport` task (HTML, XML), remove the UI and MainActivity exclusions, set the gate to the measured baseline rounded down, and print totals in the CI summary; verify `./gradlew jacocoCoverageVerification` passes and the summary shows the number
- [ ] 2.6 Design tokens in `ui/theme` (color roles, type scale, shapes, spacing, motion durations and easing) and extend `PaletteContrastTest` to every color-role pair in both themes, with the 3:1 rule for large text; test scenario "Dark theme"; update `docs/design/visual-review.md`
- [ ] 2.7 Restyle the shared components (rows, chips, empty states, form fields, buttons) to the tokens with 48 dp touch areas; re-record their Roborazzi screenshots at 360 and 840 dp in both themes; test scenario "Small icon button"
- [ ] 2.8 Update `docs/dev/testing.md` (coverage counting UI, the ratchet) and a new `docs/design/design-system.md` (tokens, components, checklist); verify the documented commands run as written

## 3. Self-contained scanning and phones without Play services (0.17)

- [ ] 3.1 Add the `QrReader` interface with an ML Kit bundled implementation and a fake; replace the Google code scanner with a CameraX scanner screen reading QR only; remove `play-services-code-scanner`; verify the existing QR unit and screen tests pass against the fake
- [ ] 3.2 Camera permission flow: ask on first scan with a one-line reason, explain and offer settings and typing when refused; tests for scenarios "First scan" and "Permission refused"
- [ ] 3.3 Test scenarios "Label from another program" and "Offline phone without Google Play services" on the device suite (scanner reads a QR fixture shown on a virtual scene or injected frame); update `maestro/COVERAGE.md`
- [ ] 3.4 `AppContainer.playServices` check; sync, tips and rating explain when it is missing, and the automatic rating prompt never shows; tests for scenarios "Opening sync without Play services" and "No Google Play"
- [ ] 3.5 Add a device-test cell on an Android image without Google APIs running the core flows; test scenario "Huawei phone"
- [ ] 3.6 Update PRIVACY.md, the privacy page linked from About, and the Play data safety notes for the camera permission and ML Kit's metrics; test scenario "Reading the disclosure"
- [ ] 3.7 Update the user guide (scanning, permission) in both languages, CHANGELOG, and the offline FAQ answer; test scenario "Scanning offline on first launch"

## 4. Attribute suggestions (0.18)

- [ ] 4.1 `assets/attribute-rules.json` (brands with aliases, unit patterns in English and Portuguese) and `AttributeRules` as a pure function; unit tests for scenarios "Brand and memory", "Ambiguous number" and "Nothing to suggest"
- [ ] 4.2 Attribute chips in the item form; tapping fills Brand/Model, a matching house-wide field, or a new one-off field; tests for scenarios "Existing house-wide field" and "New one-off field"
- [ ] 4.3 Schema change for `attribute_choice` (in the version 7 migration, task 5.1, if both ship together; otherwise its own version) and learning; test scenario "My own field name"
- [ ] 4.4 Maestro flow for attribute chips (Android 9 and 16 local cells); update `maestro/COVERAGE.md`, the user guide and CHANGELOG

## 5. Item recognition (0.19)

- [ ] 5.1 Migration to schema version 7 (`photo_label`, `photo_label_state`, `attribute_choice`, FTS recreated with the `photos` column and triggers); migration test from v6 data checking search results and that the sync export is unchanged
- [ ] 5.2 `assets/image-labels.json` mapping every ML Kit base label to English and Portuguese words and, where fitting, a category key; a test checks every label has both languages
- [ ] 5.3 `Recognizer` interface with an ML Kit implementation (labels and text, downscaled input, lazy clients) and a fake; unit tests of the name heuristic over a fixture set of packaging photos' recognized text
- [ ] 5.4 Prefill in the form: only empty fields, suggested marking, tag chips from labels, categories and attributes from text plus labels, progress while running; tests for scenarios "Photo of a packaged product", "Name already typed", "Nothing recognized" and "Suggested from a photo"
- [ ] 5.5 Identify action in the form and photo-first prefill; tests for scenarios "Identify on an existing item", "Photo cancelled" (both), "Photo first" and the 3-photo limit
- [ ] 5.6 Languages: tests for scenarios "Portuguese app" and "Accented packaging"
- [ ] 5.7 Instrumented timing test on the reference emulator over three fixture photos (3 s each) and a cold-start check that no model is loaded; tests for scenarios "Slow phone", "Cold start", "First use in airplane mode" and "Phone without Google Play services"
- [ ] 5.8 Maestro flows for Identify and photo-first prefill using fixture photos pushed by the device helper; update `maestro/COVERAGE.md`, the user guide, CHANGELOG and `FEATURE_RECOGNITION` in docs/dev

## 6. Photo search (0.20)

- [ ] 6.1 Label every newly identified photo and enqueue the background WorkManager job (batches of 20, battery not low); tests for scenario "Updating with 500 photos" with a fake recognizer and a fake clock
- [ ] 6.2 Search "Photos" match option and labels in "Name and description"; tests for scenarios "Finding by what the photo shows" and "Photos only"
- [ ] 6.3 Search from a photo: camera or gallery, labels as an editable query, ranking by shared labels, photo not kept; tests for scenarios "Finding the drill" and "Nothing similar"
- [ ] 6.4 Network test extending `NoTrackingTest` over identify and photo search; test scenario "Recognition stays on the phone"
- [ ] 6.5 Maestro flow for search from a photo; update `maestro/COVERAGE.md`, the user guide, CHANGELOG; remove `FEATURE_RECOGNITION` once the device tests pass on all nightly cells

## 7. Wide screens and grid (0.21)

- [ ] 7.1 Window size class in `TrecosApp`; navigation rail at 600 dp or more; forms and detail screens capped at 640 dp; test scenario "Tablet" with Robolectric at 840 dp and a Roborazzi screenshot
- [ ] 7.2 Grid view (`ListView.Grid`) in every list, the view toggle cycling three views, default grid on wide windows until the user picks; tests for scenarios "Grid on a phone", "Switching to detailed view" and "Item without price"
- [ ] 7.3 State kept across rotation and resize; test scenario "Rotating while typing"
- [ ] 7.4 Add the grid to `ScrollJankTest`; add a tablet device-test cell (API 36, tablet profile) to the weekly tier; update `maestro/COVERAGE.md`, the user guide (screenshots) and CHANGELOG

## 8. Design pass and coverage (0.22)

- [ ] 8.1 Apply `trecos-design` to the remaining screens (Home, place, item, search, settings, help, sync, lock), screen by screen, each with re-recorded screenshots in both themes and languages; verify the checklist in `docs/design/design-system.md` is ticked for every screen
- [ ] 8.2 Motion: shared-element photo zoom, press feedback and checkmark motions within 250 ms, instant with "Remove animations"; tests for scenarios "Opening a photo" and "Animations removed"
- [ ] 8.3 Raise the coverage gate to 90% of lines with UI counted, adding Screen tests mapped to spec scenarios; verify `./gradlew jacocoCoverageVerification` passes at 0.90

## 9. Quality Gates

- [ ] 9.1 Build, lint and unit tests pass: `./gradlew lint testDebugUnitTest`
- [ ] 9.2 Coverage gate passes at 90% with UI counted: `./gradlew jacocoCoverageVerification`
- [ ] 9.3 APK size within the budget: `python3 scripts/check-apk-size.py`
- [ ] 9.4 Release gate: cold start within the baseline and scroll jank checked on the reference emulator (`release.yml` benchmark job)
- [ ] 9.5 Device suite: the weekly tier passes on all cells, plus the no-Google-APIs and tablet cells
- [ ] 9.6 Scenario coverage passes: `python3 scripts/check-scenario-coverage.py`
- [ ] 9.7 `openspec validate launch-features` passes, and trecos-v1 and device-test-suite are archived before this change
