# Tasks

Documents are in English (project decision #17). Tasks marked **(user)** need
the developer: accounts, consoles or GitHub settings the agent cannot change.
"Local cells" means the two local emulators from task 1.2: Android 9 / 2 GB and
Android 16 / 6 GB.

## 1. Tooling and local emulators

- [ ] 1.1 Install Maestro `cli-2.11.0` locally from its GitHub release zip with the SHA-256 checked; verify `maestro --version` prints 2.11.0
- [ ] 1.2 Install the `google_apis` x86_64 images for API 28 and 36 and create the local AVDs `trecos-api28-2g` (2048 MB, 2 cores) and `trecos-api36-6g` (6144 MB, 2 cores); verify both boot headless and `adb shell getprop ro.build.version.sdk` prints 28 and 36
- [ ] 1.3 Write `docs/dev/device-tests.md` (what the suite is, local setup, running one flow, one area or one tier on a local cell); verify by following it in a fresh shell to run one flow

## 2. App hooks for the flows

- [ ] 2.1 Set `testTagsAsResourceId = true` on `TrecosApp`'s root; verify with a Robolectric test that a tagged node exposes its tag as a resource id, and that `maestro hierarchy` on a local cell lists `id: tab_home`
- [ ] 2.2 Rename `BenchmarkSeedReceiver` to `TestSeedReceiver` with a `fixture` extra (`empty`, `one-house`, `two-houses`, `photos`, `1000-items`), enabled in `debug` and `benchmarkRelease` only; verify each fixture with a Robolectric test of the stored houses, containers, items and photos
- [ ] 2.3 Test scenario "The seeding hook in a release build": a Robolectric test with `BENCHMARK_SEED` off stores nothing, and the merged release manifest has the receiver disabled (`./gradlew :app:processReleaseMainManifest` plus a check of the output)
- [ ] 2.4 Point `StartupBenchmark` at `fixture=1000-items`; verify it still runs on the local API 28 cell with `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`
- [ ] 2.5 Add any missing test tags that flows need (listed while writing group 5), each covered by an existing or new Robolectric assertion; update `docs/architecture/overview.md` with the test-tag and seed-hook conventions

## 3. Matrix and cell runner

- [ ] 3.1 Write `scripts/device-matrix.py <tier>` printing the GitHub matrix JSON (`pr` 2 cells, `nightly` 9 cells with rotating memory, `weekly`/`release` 36 cells); verify with `scripts/test_device_matrix.py` (standard-library `unittest`)
- [ ] 3.2 Test scenario "The nightly run rotates memory": seven consecutive days give every API level all four memory sizes (in `test_device_matrix.py`)
- [ ] 3.3 Write `scripts/run-device-tests.sh <tier>`: installs the APK, runs `connectedDebugAndroidTest`, runs Maestro with JUnit output and a printed shuffle seed, reruns failures once, collects screenshots, step logs and `logcat --pid`, writes `result.json`; verify on both local cells with the `smoke` tag
- [ ] 3.4 Test scenario "A flaky flow": a fixture flow that fails on its first attempt only is reported `flaky` and the run passes (check `result.json`)
- [ ] 3.5 Test scenario "Flows run in any order": two full runs on a local cell with different shuffle seeds give the same per-flow results
- [ ] 3.6 Write `scripts/summarize-device-results.py` merging `result.json` files into a Markdown table (cell, Android version, memory, passed, failed, flaky, infrastructure), with redaction of known secret values; verify with unit tests including scenario "Secrets in a failing sign-in flow" (a planted password value never survives in any output)
- [ ] 3.7 Update `docs/dev/device-tests.md` with the tiers, the matrix, `result.json` and how to reproduce a CI run locally from its shuffle seed

## 4. Scenario coverage check

- [ ] 4.1 Write `scripts/check-scenario-coverage.py` reading the `trecos-v1` (and later `openspec/specs/`) scenarios, the flows' `# covers:` and `# screens:` headers, `maestro/COVERAGE.md` and the app's `NavHost` routes; verify with `scripts/test_check_scenario_coverage.py`
- [ ] 4.2 Test scenario "A new scenario with no flow": a planted scenario missing from every group fails the check and is named
- [ ] 4.3 Test scenario "A screen with no flow": a planted route missing from every `# screens:` header fails the check and is named
- [ ] 4.4 Create `maestro/COVERAGE.md` with the "Robolectric only" and "manual" scenarios and reasons (Play Billing tips, camera scanning, the Play services scanner download), and run the check in `ci.yml`; verify it fails on `master` until group 5 lands, then passes

## 5. Flows for every screen and feature

Each task writes the area's flows with `# covers:` and `# screens:` headers, tags the smoke subset, and is verified by the flows passing on both local cells and the coverage check counting the area's scenarios.

- [ ] 5.1 `maestro/common/`: fresh start (clear app data, launch), seed a fixture, open a tab, add a house, add a container, add an item, and system-UI branches for API 28–29, 30–32 and 33+ (photo picker, document picker, permission dialogs); verify each subflow on both local cells
- [ ] 5.2 `app-shell`: tabs and the raised circle, tab taps returning to the root, back behaviour, first launch, language switch (en/pt-BR), themes (Follow system, White, Dark), start screen, reduced motion
- [ ] 5.3 `places`: houses (create, edit, colours, icons, the house list with two or more houses, deleting the last house), nested containers, breadcrumbs, values and the "no price" count
- [ ] 5.4 `items`: create, edit and validation, quantity and price with the currency symbol, brand, model, serial, description, empty fields hidden, item screen
- [ ] 5.5 `photos`: add from the system photo picker (pushed fixture images), main photo, carousel, viewer, delete, location data removed (EXIF check of the stored file through `adb`)
- [ ] 5.6 `categories-and-tags` and `custom-fields`: two-level categories, offline suggestions, custom categories per house, tags, custom fields per house and their values
- [ ] 5.7 `search`: query with accents ignored, scopes (items, containers, both), match in, filters by house, category and tag, "Clear all", sort, "search in this container", typed QR code lookup, filters kept across restarts
- [ ] 5.8 `qr-codes`: generate, duplicates refused, print through the system print dialog (save as PDF), share, label sheet; camera scanning listed as manual in `COVERAGE.md`
- [ ] 5.9 `organize` and `trash`: move, copy and duplicate within and between houses, the 30-day trash, restore, restore when the original place is gone, permanent delete, deleted houses
- [ ] 5.10 `backup`: export to a `.zip` through the document picker, import preview, add houses, replace everything, the unencrypted-backup warning with encryption on
- [ ] 5.11 `settings` and `app-lock`: every setting persists across restarts, app lock with an emulator fingerprint (`adb emu finger touch`) and PIN, lock timeout, "lock turned off" when the screen lock is removed
- [ ] 5.12 `encryption`: turn on with the experimental warning, restart, turn off, export while encrypted; plus the `roundtrip`-tagged scenario "Encryption round trip keeps the data" on the `photos` fixture
- [ ] 5.13 `help-and-support`: FAQ accordion, "Contact us" opening an e-mail intent (and the copy fallback without an e-mail app), About links (website, privacy, source, licences), the tips screen when Play Billing is unavailable
- [ ] 5.14 Verify the whole suite: all flows pass on both local cells in two shuffled orders, and `scripts/check-scenario-coverage.py` passes on the branch

## 6. CI workflow

- [ ] 6.1 Write `.github/workflows/device-tests.yml`: matrix job from `device-matrix.py`, one job per cell with `reactivecircus/android-emulator-runner@v2.38.0`, the KVM step, cached boot snapshots, `fail-fast: false`, `max-parallel: 20`, a 10-minute boot timeout, Maestro installed with its checksum, and `run-device-tests.sh`; verify a green `pr` tier on the pull request
- [ ] 6.2 Add the summary job (merged table on the run page) and artifact upload with 14 days' retention; verify on the pull request run that the table and artifacts appear
- [ ] 6.3 Test scenario "A pull request breaks adding an item": on a throwaway branch, a deliberate break in the add-item flow fails the device check, which names the flow and the cell; close the branch without merging
- [ ] 6.4 Test scenario "An emulator fails to boot": a cell with an impossible image (temporary matrix override) is reported "infrastructure failure" while the other cell still passes
- [ ] 6.5 **(user)** Mark the `pr` device check as required in the branch rules for `master`; verify the merge button waits for it on the next pull request
- [ ] 6.6 Add the nightly and weekly schedules and run the weekly tier once by `workflow_dispatch`; verify 36 cells report results (scenario "A weekly full run"), and fix or tag as flaky any flow that fails only on some cells
- [ ] 6.7 Update `docs/dev/device-tests.md` and `docs/dev/testing.md` with the CI tiers, where to find results, and the required check

## 7. Drive flows with the Google test account

- [ ] 7.1 **(user)** Create a dedicated Google account for tests (never a personal one) and add it as a test user on the "Trecos" OAuth consent screen; verify it can sign in on a local emulator
- [ ] 7.2 Generate a fixed CI debug keystore and print its SHA-1; **(user)** register it as the Android OAuth client "Trecos CI debug" (package `app.trecos`); verify a debug build signed with it can sign in on a local cell
- [ ] 7.3 **(user)** Create a Desktop OAuth client and, with a script the agent provides, a refresh token for the test account; verify `scripts/clean-test-drive.py` lists the account's Trecos folder locally
- [ ] 7.4 **(user)** Create the GitHub environment `google-test` (limited to `master` and `v*` tags) with the secrets `TEST_GOOGLE_ACCOUNT`, `TEST_GOOGLE_PASSWORD`, `TEST_GOOGLE_DRIVE_TOKEN`, `TEST_GOOGLE_OAUTH_CLIENT` and `CI_DEBUG_KEYSTORE`, through a script in `~/` run with `! bash`; verify `gh secret list --env google-test` names all five
- [ ] 7.5 Add `maestro/.env.example` with placeholders for those values and confirm `maestro/.env` is gitignored (`git check-ignore maestro/.env`)
- [ ] 7.6 Write `scripts/clean-test-drive.py` (standard library only) and run it before the Drive flows; test scenario "Clean Drive before a run": after it, the account has no Trecos folder
- [ ] 7.7 Write the account sign-in subflow for API 28 and 36 (Settings > Accounts, password from the environment) with challenge detection; test scenario "Google challenges the sign-in" by forcing the challenge branch, which reports `sign-in-blocked` without failing the run
- [ ] 7.8 Write the `drive` flows (connect, sync, photos uploaded, disconnect, deleted houses and their restore) and the `roundtrip` flow "Sync round trip on a fresh phone" across two app installs; verify on both local cells with the test account
- [ ] 7.9 Wire the Drive jobs into `device-tests.yml` (nightly on API 36 / 4 GB, weekly and release on the reference emulator and API 36) using the `google-test` environment and the CI keystore; verify one nightly run and that pull request runs never receive the secrets
- [ ] 7.10 Update `docs/dev/device-tests.md` with the test account setup, the secrets by name, and what "sign-in-blocked" means

## 8. Reference emulator and release gate

- [ ] 8.1 Add the `benchmark` job to `release.yml` on the reference emulator (API 28, 2048 MB, 2 cores, `google_apis` x86_64), running `StartupBenchmark` and `scripts/check-benchmark.py`, and make `publish` need it and the `release` device tier; verify with a `workflow_dispatch` dry run (publishing skipped) that the job runs and the summary shows the numbers, runner and image revision
- [ ] 8.2 Test scenarios "A release within budget" and "A slow build blocks the release" with `check-benchmark.py` unit tests on sample Macrobenchmark JSON (1.2 s / 2% passes, 1.7 s fails with measured and allowed values)
- [ ] 8.3 Update `trecos-v1`: the `app-shell` requirement "Performance on low-end phones" names the reference emulator instead of the reference phone, D19's `FEATURE_ENCRYPTION` criterion names the round-trip flows on the reference emulator and Android 16, and tasks 14.7 and 14.8 refer to them; verify `openspec validate trecos-v1` passes
- [ ] 8.4 Update `docs/dev/release.md` (the benchmark gate and the device tier in the release) and `docs/decisions/` with an ADR for the reference emulator replacing the physical phone

## 9. Quality Gates

- [ ] 9.1 Build compiles cleanly: `./gradlew assembleDebug assembleRelease`
- [ ] 9.2 Lint passes with no new warnings: `./gradlew lintDebug`
- [ ] 9.3 Unit and screenshot tests pass: `./gradlew testDebugUnitTest verifyRoborazziDebug`
- [ ] 9.4 Coverage threshold holds: `./gradlew jacocoCoverageVerification`
- [ ] 9.5 Script tests pass: `python3 -m unittest discover -s scripts -p 'test_*.py'`
- [ ] 9.6 Scenario coverage passes: `python3 scripts/check-scenario-coverage.py`
- [ ] 9.7 The OpenSpec change validates: `openspec validate device-test-suite --strict`
- [ ] 9.8 Secret scan passes over the branch: `pre-commit run gitleaks --all-files`
