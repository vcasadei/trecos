# 0030. No Google sign-ins from CI

- **Status:** Accepted
- **Date:** 2026-10-15
- **Change:** `device-test-suite` (spec device-testing, "Google test account" and "Encryption and sync round trips")

## Context

The Drive and round-trip flows sign in through Google's own screens with a
dedicated test account. From the developer's machine they pass.

The first release runs that included them (v0.15.0 and v0.15.1) signed in from
GitHub's shared runners, a different machine and network each time, 9 to 18
sign-ins per run. Google answered with:
- "Check your <device>": approve the sign-in on a phone that already has the
  account;
- "Verify it's you" with only a phone prompt, "Unavailable because of too many
  attempts";
- a "Someone has your password" security alert on the account.

The account has no 2-step verification. Google treats sign-ins from many
places in a short time as an attack on the account, so this won't settle down,
and more attempts put the account at risk of being locked.

## Decision

- **CI signs in to nothing.** The Drive job is off (`DRIVE_IN_CI` in
  `scripts/device-matrix.py`): nightly, weekly and release runs list no Drive
  cells.
- **Drive flows run from one fixed machine.** Before every release tag, the
  developer's machine runs the `drive` tier, round trips included, on an
  Android 16 emulator. Google accepts sign-ins from a place it already knows.
- **Next:** run the Drive flows in CI without Google's sign-in screens. A
  debug-build-only hook would give the app an access token for the test
  account, kept out of release builds by a test. The CI Drive job comes back on
  once that is agreed and built.

## Consequences

- No more failed sign-ins pile up on the test account.
- Releases no longer wait on Drive cells that Google blocks.
- Until the token hook exists, a Drive regression is caught only by the run
  from the developer's machine, so the release checklist must include it.
- Google's sign-in screens themselves are tested only from the developer's
  machine and on a real phone, also after the hook exists.
