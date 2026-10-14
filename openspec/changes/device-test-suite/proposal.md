# Proposal

## Why

Trecos has 427 Robolectric tests but only four tests on a real Android runtime,
and nobody can test on Android 9, the minimum version, because the developer
has no Android 9 phone. Two open release tasks (14.7 flag removal, 14.8
performance pass) are blocked on that phone. An automated device suite on
emulators closes both gaps before Google Play closed testing starts.

## What Changes

- An end-to-end suite of **Maestro** flows (YAML, in the repository) that covers
  every screen and every user-facing feature, traced to the `trecos-v1` spec
  scenarios, with a coverage report that lists which scenarios each flow checks
  and which are covered only by Robolectric tests or manual checks.
- The app exposes its Compose test tags as resource ids so flows can find
  elements without depending on text or screen coordinates.
- A **device matrix** of emulators from Android 9 (API 28) to Android 16
  (API 36) and memory sizes of 2, 3, 4 and 6 GB, run in GitHub Actions:
  - every pull request: the smoke flows on the two corners of the matrix
    (Android 9 with 2 GB, Android 16 with 6 GB);
  - every night on `master`: the full suite once per Android version, with the
    memory size rotating;
  - every week and before each release: the full suite on all 36 cells.
- The existing device tests in `app/src/androidTest` (SQLCipher, encryption,
  backup, photos) run in the same matrix.
- A **reference emulator** (Android 9, 2 GB RAM, fixed CPU count and disk
  image) runs the Macrobenchmark cold-start and 1,000-item scroll benchmarks,
  and the release workflow gates on it.
- The encryption and sync on/off round trips that D19 requires become
  automated flows on the reference emulator and on Android 16; the sync flows
  use a dedicated Google test account.
- Results (pass/fail per flow, screenshots of failures, benchmark numbers) are
  uploaded as CI artifacts and summarised on the run page.
- No **BREAKING** changes: the app's behaviour and data are unchanged.

## Capabilities

### New Capabilities
- `device-testing`: the device test matrix, the scenario coverage the Maestro
  suite must reach, when each tier runs, how failures are reported, the
  reference emulator, and the release gate on the performance benchmark.

### Modified Capabilities
None under `openspec/specs/`: the main specs do not exist yet because
`trecos-v1` is not archived. The two `trecos-v1` texts that name a physical
Android 9 phone are updated in place by this change's tasks, since that change
is still in progress:
- `trecos-v1` spec `app-shell`, requirement "Performance on low-end phones":
  "a reference phone with Android 9 and 2 GB RAM" becomes the reference
  emulator defined in `device-testing`.
- `trecos-v1` design D19: the `FEATURE_ENCRYPTION` removal criterion "on/off
  round trips pass on a physical Android 9 phone and on a current Android
  phone" becomes the automated round-trip flows on the reference emulator and
  on Android 16.

## Impact

- **New tools, approved by the user on 2026-10-12**: the Maestro CLI (Apache
  2.0, run locally and in CI; no Maestro Cloud account). Pinned to an exact
  version.
- **New CI dependency, approved by the user on 2026-10-12**: the
  `reactivecircus/android-emulator-runner` GitHub Action (Apache 2.0) to boot
  emulators with KVM on GitHub's Linux runners, pinned to an exact version.
- **App code**: one root-level `testTagsAsResourceId` semantics flag, any
  missing test tags on controls the flows need, and the existing benchmark seed
  receiver extended with named fixture sets and enabled in debug builds (still
  ignored by release builds). No behaviour change.
- **CI**: new workflows (`device-tests.yml`, scheduled runs) and a benchmark
  step in `release.yml`. GitHub-hosted runners are free for this public
  repository; the weekly 36-cell run takes the most time.
- **Fixture data**: flows use invented data on throwaway emulators;
  screenshots in CI artifacts show only that data.
- **Drive sync on a real Google account**: the user chose (2026-10-12) a
  dedicated Google test account, never a personal one, holding only fixture
  data. Flows sign in on an emulator with Google Play services and run sync,
  restore and the deleted-house flows against that account's Drive. Google may
  challenge or block automated sign-ins, so these flows run on a schedule on one
  cell and do not block pull requests.
- **New secrets** (GitHub secrets, by name only): `TEST_GOOGLE_ACCOUNT` and
  `TEST_GOOGLE_PASSWORD` for the test account, `TEST_GOOGLE_RECOVERY_EMAIL`
  (its recovery address, which answers Google's "Verify it's you" on a new
  emulator), `TEST_GOOGLE_DRIVE_TOKEN` (an OAuth refresh token on that account,
  used only to empty its Trecos folder before a run), `TEST_GOOGLE_OAUTH_CLIENT`
  (the "TVs and Limited Input" OAuth client that refresh token belongs to), and `CI_DEBUG_KEYSTORE` (a fixed
  debug keystore whose SHA-1 is registered as an OAuth client, so sign-in works
  on any runner). The user creates the account and the secrets.
- **Not automatable on emulators**: Google Play Billing tips and camera QR
  scanning. They stay covered by Robolectric tests with fakes and are listed
  in the coverage report as manual checks.
- **Personal data (LGPD)**: the test account is a throwaway identity with no
  real personal data; its Drive holds only fixture houses.
- Database schema, public APIs and the release signing setup are unchanged.
