# Spec Delta

## Purpose

Checks every screen and feature of Trecos on real Android runtimes, from
Android 9 to Android 16 and from 2 GB to 6 GB of memory, automatically on every
change, and gates releases on performance measured on a reference emulator.

## ADDED Requirements

### Requirement: Device matrix
The device suite SHALL run on emulators covering every Android version from 9
(API 28) to 16 (API 36) and the memory sizes 2, 3, 4 and 6 GB, 36 cells in all.
Each cell MUST be recorded in the results with its Android version, API level
and memory size.

#### Scenario: A weekly full run
- **AS A** the scheduled weekly run
- **WHEN** it starts on `master`
- **THEN** the full suite runs on all 36 cells
- **AND** the run summary lists each cell with its result

#### Scenario: An emulator fails to boot
- **AS A** the device suite
- **WHEN** one cell's emulator does not finish booting within 10 minutes
- **THEN** that cell is reported as "infrastructure failure", distinct from a test failure
- **AND** the other cells still run and report their own results

### Requirement: Run tiers
The suite SHALL run in three tiers. Every pull request MUST run the smoke flows
on the Android 9 / 2 GB and Android 16 / 6 GB cells and MUST fail the pull
request's checks when a smoke flow fails. Every night, the full suite MUST run on
`master` once per Android version, with the memory size rotating so that every
memory size is used within a week. Every week, and for every release tag, the
full suite MUST run on all 36 cells.

#### Scenario: A pull request breaks adding an item
- **AS A** developer opening a pull request
- **WHEN** a change makes the "add an item" smoke flow fail on Android 9 / 2 GB
- **THEN** the pull request's device check fails
- **AND** the check names the flow and the cell

#### Scenario: The nightly run rotates memory
- **AS A** the nightly run
- **WHEN** it runs on seven consecutive nights
- **THEN** each Android version has run with each of the four memory sizes at least once in that week

### Requirement: Scenario coverage
The Maestro flows SHALL cover every screen of the app and every user-facing
`trecos-v1` scenario that can run on an emulator. A coverage report in the
repository MUST map each `trecos-v1` scenario to the flows that check it, or
mark it "device test" (an instrumented test run on the same cells),
"Robolectric only" or "manual", with a reason. The suite MUST fail when
a scenario is in none of those groups.

#### Scenario: A new scenario with no flow
- **AS A** developer
- **WHEN** a spec scenario is added and the coverage report does not mention it
- **THEN** the coverage check fails and names the unmapped scenario

#### Scenario: A screen with no flow
- **AS A** the coverage check
- **WHEN** a navigation destination of the app is not visited by any flow
- **THEN** the check fails and names the destination

### Requirement: Isolated and repeatable flows
Each flow MUST start from a freshly installed app with no data, set up its own
fixture data through the app's screens or through a seeding hook that exists
only in test builds, and leave nothing that another flow depends on. A release
build MUST ignore the seeding hook. Flows SHALL find elements by
test tag or by text visible to the user, never by screen coordinates. A flow
that passes MUST pass again when rerun unchanged on the same cell.

#### Scenario: Flows run in any order
- **AS A** the device suite
- **WHEN** it runs the full suite in a shuffled order
- **THEN** every flow gives the same result as in the default order

#### Scenario: The seeding hook in a release build
- **AS A** anyone with adb access to a phone running a release build
- **WHEN** they send the seeding broadcast
- **THEN** the app stores nothing

#### Scenario: A flaky flow
- **AS A** the device suite
- **WHEN** a flow fails and then passes on the one automatic retry
- **THEN** the run passes but reports the flow as "flaky" in the summary

### Requirement: Failure evidence
For every failed flow, the run SHALL keep a screenshot of the screen at the
moment of failure, the flow's step log and the device log from the app's
process, for 14 days. These files MUST NOT contain secrets: the test account's
password and the keystore are never written to logs or artifacts.

#### Scenario: Diagnosing a failure
- **AS A** developer
- **WHEN** a flow fails in CI
- **THEN** the run page links to an artifact holding the failure screenshot, the step log and the app log

#### Scenario: Secrets in a failing sign-in flow
- **AS A** the device suite
- **WHEN** the Drive sign-in flow fails while typing the test account's password
- **THEN** the step log shows the password step masked
- **AND** no artifact contains the password

### Requirement: Reference emulator and performance gate
A reference emulator SHALL be defined as Android 9 (API 28), 2 GB of memory,
2 CPU cores and a fixed system image. It replaces the physical reference phone
for the release gate in the `trecos-v1` performance requirement and in design
D19.

On it, the release benchmark MUST measure, with 1,000 items stored:
- the median cold start over 10 runs;
- the share of janky frames while scrolling those items.

Emulator timings don't match a real phone, and a software GPU makes almost
every frame janky, so the emulator gates regressions, not the absolute budget:
- A release tag MUST NOT publish its APKs when the median cold start is more
  than 15% slower than the recorded baseline for the same runner type and
  system image.
- The jank share MUST be recorded but does not gate on the emulator.
- The baseline, a committed file naming its runner type and image, MUST change
  only on purpose, for example when the runner or image changes.

The absolute budgets stay with real hardware. Before a build is promoted on
Google Play, the same benchmark MUST run on a low-end physical phone in
Firebase Test Lab: a median cold start of at most 1.5 s, and fewer than 5%
janky frames.

#### Scenario: A release within the baseline
- **AS A** the release workflow
- **WHEN** the reference benchmark measures a 1,900 ms median cold start against a 1,800 ms baseline
- **THEN** the release continues to the approval step
- **AND** the run summary shows the measured value, the baseline, the runner and the jank share

#### Scenario: A slow build blocks the release
- **AS A** the release workflow
- **WHEN** the reference benchmark measures a 2,200 ms median cold start against a 1,800 ms baseline
- **THEN** the release stops before publishing
- **AND** the summary shows the measured value, the baseline and the allowed maximum (2,070 ms)

#### Scenario: Budgets on a real phone
- **AS A** the developer promoting a build on Google Play
- **WHEN** the Firebase Test Lab run on a low-end physical phone measures a 1.7 s median cold start
- **THEN** `scripts/check-benchmark.py --budget` fails with the measured and allowed values
- **AND** the build is not promoted

### Requirement: Encryption and sync round trips
The suite SHALL include round-trip flows that turn encryption on and off and
sync on and off, checking that every house, item and photo is unchanged after
each switch. They MUST run on Android 16, with 2 GB and with 4 GB of memory,
in the weekly run and for every release tag. These results are the D19
removal criterion for `FEATURE_ENCRYPTION`. Older emulator images can't run
them: their Google Play services is too old to authorize Drive access and
can't update.

#### Scenario: Encryption round trip keeps the data
- **AS A** the round-trip flow
- **WHEN** it turns encryption on, restarts the app, turns encryption off and restarts again
- **THEN** every fixture house, item and photo is shown as before

#### Scenario: Sync round trip on a fresh phone
- **AS A** the round-trip flow signed in with the test account
- **WHEN** it syncs from one emulator, then signs in on a freshly installed app and restores
- **THEN** the restored houses, items and photos match the originals

### Requirement: Google test account
Flows that need Google Drive SHALL sign in with a dedicated Google test account
that holds only fixture data and is never a personal account. Before each run
they MUST delete the account's Trecos folder so runs do not see each other's
data. These flows MUST NOT block pull requests: when Google blocks or
challenges the sign-in, the run reports "sign-in blocked" instead of a test
failure.

#### Scenario: Clean Drive before a run
- **AS A** the Drive flows
- **WHEN** a scheduled run starts
- **THEN** the test account's Trecos folder is deleted before the first flow

#### Scenario: Google challenges the sign-in
- **AS A** the Drive flows
- **WHEN** Google asks for extra verification during sign-in
- **THEN** the Drive flows are reported as "sign-in blocked"
- **AND** the rest of the run's results are unaffected
