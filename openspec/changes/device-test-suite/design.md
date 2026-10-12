# Design

## Context

See proposal.md for the motivation. What exists today:

- 427 Robolectric tests in `app/src/test`, plus Roborazzi screenshots. They run
  in `ci.yml` without an emulator.
- Four instrumented tests in `app/src/androidTest` (SQLCipher, encryption,
  backup, photos). CI never runs them.
- The `baselineprofile` module holds `StartupBenchmark` (cold start over 10
  runs, scrolling 1,000 items) and `BaselineProfileGenerator`.
  `scripts/check-benchmark.py` applies the 1.5 s / 5% budget. The data comes
  from `BenchmarkSeedReceiver`, which was on only in `benchmarkRelease`
  (manifest placeholder plus `BuildConfig.BENCHMARK_SEED`).
- The Compose UI already uses `testTag` widely (for example `rowTag(id)`,
  `tabTag(tab)`, `about_privacy`), because the Robolectric tests depend on it.
- Signing: the release keystore lives only in the `release` environment.
  Debug builds use each machine's own debug keystore. The "Trecos debug" OAuth
  client is registered with the developer machine's SHA-1.

## Goals / Non-Goals

**Goals:**
- Flows that a developer can run locally with one command against one
  emulator, and that CI runs unchanged.
- A matrix definition in one place, used by every tier.
- The benchmark gate inside `release.yml`, before the approval step.

**Non-Goals:**
- Replacing the Robolectric tests. Flows cover journeys and real-runtime
  behaviour; detailed logic stays in fast unit tests.
- Pixel screenshot comparison on devices. Roborazzi already covers visuals,
  and emulator rendering differs between API levels.
- Tablets, foldables, landscape and non-default font scales. The app is
  phone-portrait first. Text safety at large font scales is already covered by
  `TextSafetyTest`.
- Maestro Cloud, Firebase Test Lab and physical devices.

## Decisions

### 1. Maestro CLI, pinned
Maestro is pinned to `cli-2.11.0` and installed in CI from the GitHub release
zip, with its SHA-256 checked. Local setup is documented in
`docs/dev/device-tests.md`.
- **Alternatives:** Espresso or Compose instrumented tests (more Kotlin to
  write, and weak with system UI such as the photo picker, account settings
  and the document picker); `android-qa-agent` and OpenTester (AI-driven, so
  results vary between runs, and they cost money or send screenshots to a
  third party). The user approved Maestro on 2026-10-12.

Layout:
- `maestro/flows/<area>/*.yaml`: one area per `trecos-v1` capability, for
  example `places`, `items`, `search` and `qr-codes`.
- `maestro/common/*.yaml`: subflows reused by many flows (fresh start, seed,
  open a tab, add an item).
- Maestro tags select the tier: `smoke`, `full`, `drive` and `roundtrip`.
- Every flow starts with a header comment listing the scenarios it covers
  (`# covers: places/"Creating a second house"`) and the screens it visits
  (`# screens: house_list, container`).

### 2. Test tags as resource ids
The activity's Compose root (around the lock gate and `TrecosApp`) sets
`Modifier.semantics { testTagsAsResourceId = true }`,
so Maestro's `id:` selectors match the existing Compose test tags, the lock
screen's included. Dialogs are separate windows that do not inherit the flag,
so flows find dialog controls by their visible text. It is set in
every build, so CI tests the same UI tree users get. The cost is extra resource
ids in the accessibility tree, which expose nothing private.
- **Alternative:** debug-only. Rejected: two UI trees would mean flows test
  something different from what ships.

### 3. Fixtures through the existing seed receiver
`BenchmarkSeedReceiver` becomes `TestSeedReceiver`, with a `fixture` extra naming a set:
- `one-house`: an apartment with an office, two boxes and four items (one without a price);
- `two-houses`: the same plus a second house, for the house list and moves between houses;
- `photos`: `one-house` with three photos on the laptop, generated as plain
  bitmaps and stored through the real photo pipeline;
- `1000-items`: today's benchmark data.

A fresh install already has no data (the first-run prompt), so there is no
`empty` fixture: flows that need it clear the app's data. The receiver is
enabled in the `debug` and `benchmarkRelease` builds (`BuildConfig.TEST_SEED`
and the `testSeed` manifest placeholder) and stays disabled in `release`, where
the check also returns early. The benchmark calls it with `fixture=1000-items`.
Flows that test creation, import or restore build their data through the UI.
- **Why reuse:** the benchmark is the concrete second user of this receiver, so
  there is one seeding path, not two.
- **Alternative:** importing a fixture backup through the document picker at
  the start of each flow. Rejected: it adds about 20 seconds to every flow
  across 36 cells. Import is tested once, by its own flows.

### 4. Emulator images and memory
- **Images:** `system-images;android-<api>;google_apis;x86_64` for API 28 to 36.
  The Drive cells use `google_apis_playstore` (API 28 and 36), for up-to-date
  Play services and account sign-in.
- **Memory:** the AVD gets `hw.ramSize` and `-memory` set to the cell's size,
  2 CPU cores, `-no-snapshot-save`, `-no-audio` and `-no-boot-anim`.
- **Snapshot cache:** a clean-boot snapshot is cached per API level and image
  with `actions/cache`, keyed on the image's revision, so boots stay short.
- **Reference emulator:** API 28 `google_apis` x86_64, 2048 MB, 2 cores, on
  `ubuntu-24.04`. The image revision is recorded in the results.

### 5. One matrix script for every tier
`scripts/device-matrix.py <tier>` prints the GitHub Actions matrix as JSON:
- `pr` gives 2 cells;
- `nightly` gives 9 cells, one per API level, with memory index
  `(day_of_year + api) % 4`, so each API level gets every memory size within
  four nights;
- `weekly` and `release` give all 36 cells.

`device-tests.yml` runs on `pull_request`, `schedule` (nightly at 03:00 UTC,
weekly on Sunday) and `workflow_call` from `release.yml`. A first job computes
the matrix. Then one job runs per cell, with `fail-fast: false` and
`max-parallel: 20`. The emulator starts with
`reactivecircus/android-emulator-runner@v2.38.0`, after the KVM udev step its
documentation gives. The user approved this action on 2026-10-12.

### 6. Running a cell
`scripts/run-device-tests.sh <tier>` runs on a cell, locally or in CI:
1. Installs the debug APK, or the `benchmarkRelease` APK for the benchmark.
2. Runs `connectedDebugAndroidTest` for the four existing instrumented tests.
3. Runs Maestro over the tier's tags with JUnit output. The order is shuffled
   with a seed that is printed and kept, so a run can be repeated.
4. Reruns failed flows once. A flow that passes on the rerun is marked "flaky".
5. Saves the Maestro screenshots and step logs and `logcat --pid` for the app
   to `build/device-results/<cell>/`.
6. Writes `result.json`, with the cell, the image revision, the runner, each
   flow's pass, fail or flaky status, the shuffle seed, and an
   `infrastructure` flag when the emulator did not boot.

A final job in the workflow merges every cell's `result.json` into the run
summary table. It uploads the artifacts with 14 days' retention.

### 7. Coverage check without an emulator
`scripts/check-scenario-coverage.py` runs in `ci.yml`. It reads:
- every `#### Scenario:` in `openspec/changes/trecos-v1/specs/*/spec.md` (and
  in `openspec/specs/` once `trecos-v1` is archived);
- the `# covers:` and `# screens:` headers of every flow;
- `maestro/COVERAGE.md`, which lists the scenarios marked "Robolectric only" or
  "manual" with a reason;
- the navigation routes declared in the app's `NavHost`.

It fails when a scenario is in no group, or when a route is in no flow's
`# screens:` header. It is a plain Python script with no dependencies, like the
existing `scripts/check-benchmark.py`.

### 8. Benchmark gate in the release
`release.yml` gains a `benchmark` job on the reference emulator:
- It runs `:baselineprofile:connectedBenchmarkReleaseAndroidTest` with
  `androidx.benchmark.suppressErrors=EMULATOR`, because Macrobenchmark refuses
  emulators otherwise.
- `scripts/check-benchmark.py` then applies the budget.

The `publish` job needs both `build` and `benchmark`, so a failing budget stops
the release before the approval request. The `release` tier of
`device-tests.yml` runs as a needed job too, including the round-trip flows.
The release run summary shows the benchmark numbers with the runner and the
image revision.

### 9. Drive flows with the test account
- **Signing:** debug builds in CI are signed with a fixed debug keystore from
  the `CI_DEBUG_KEYSTORE` secret. Its SHA-1 is registered as a third Android
  OAuth client, "Trecos CI debug", so sign-in works on any runner. Local runs
  keep the developer's own debug keystore.
- **Account:** a Maestro subflow adds the account through the system's
  Settings > Accounts, with `inputText: ${TEST_GOOGLE_PASSWORD}`. The value
  comes from the environment and is never written in a flow.
- **Clean Drive:** before the Drive flows, `scripts/clean-test-drive.py` swaps
  `TEST_GOOGLE_DRIVE_TOKEN` (a refresh token for the throwaway account, created
  once with the full `drive` scope through a Desktop OAuth client) for an
  access token and deletes the `Trecos` folder. It uses only the Python
  standard library.
- **Blocked sign-in:** if Google shows a challenge screen ("Verify it's you"),
  the subflow detects it and marks the Drive flows `sign-in-blocked` instead of
  failed.
- **Schedule:**
  - nightly: the `drive` flows on one cell (API 36, 4 GB);
  - weekly and release: the `roundtrip` flows on the reference emulator and on
    API 36.

## Security & Observability

- **Secrets:** `TEST_GOOGLE_ACCOUNT`, `TEST_GOOGLE_PASSWORD`,
  `TEST_GOOGLE_DRIVE_TOKEN`, `TEST_GOOGLE_OAUTH_CLIENT` (the Desktop client's id
  and secret, used for the token swap) and `CI_DEBUG_KEYSTORE` (base64).
  - They live in a new GitHub environment, `google-test`, limited to `master`
    and `v*` tags, so pull requests (including forks) never receive them.
  - Only the Drive and round-trip jobs use that environment. The repository
    owner is the only person who can read or change them.
  - Locally, a developer who wants to run Drive flows exports them from a
    gitignored `maestro/.env`. The committed `maestro/.env.example` holds only
    placeholders.
- **Redaction:** after every run, `run-device-tests.sh` replaces the password
  and token values in every file under `build/device-results/` with `***`
  before upload. This is a second guard, on top of Maestro's own masking of
  values read from the environment.
- **Personal data:** none. Fixtures are invented, and the test account holds
  nothing else.

## Risks / Trade-offs

- [Emulator timings differ from real 2 GB phones] → The budget stays the same,
  but results carry the runner type and image revision. Trends are compared
  only within the same runner. The developer can still run the benchmark on any
  real phone with the same script.
- [Flaky boots or slow runners on GitHub] → One rerun per flow, the "flaky"
  label, infrastructure failures reported apart from test failures, and cached
  boot snapshots.
- [Google blocks automated sign-in on the test account] → The Drive flows never
  block pull requests, and the summary reports "sign-in-blocked". If blocks
  persist, the round-trip criterion falls back to the encryption flows plus a
  manual sync check, and the user is told.
- [Very long weekly runs: 36 cells × the full suite] → `max-parallel: 20`,
  shuffled but sharded flows if one cell exceeds 60 minutes, and the weekly run
  on Sunday night.
- [Photo picker, document picker and Settings differ by API level] → Common
  subflows branch on visible text with Maestro's `runFlow: when:` conditions,
  one per system-UI generation (API 28–29, 30–32, 33+).
- [Maestro versions break flows] → The version is pinned, and upgrades are
  their own pull request with a full run.

## Open Questions

- The exact Maestro subflow for Settings > Accounts on API 28 versus 36 is
  settled while writing it on the local emulators.
