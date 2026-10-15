# 0029. A warning before the performance gate

- **Status:** Accepted
- **Date:** 2026-10-15
- **Change:** `device-test-suite` (spec device-testing, "Reference emulator and performance gate"); supersedes the 15% limit of [0028](0028-reference-emulator-and-the-performance-gate.md)

## Context

ADR 0028 stopped a release when the cold start on the reference emulator was
more than 15% slower than the recorded baseline.

The v0.15.1 release measured 3,785 ms against a 3,081 ms baseline, 23% slower,
and was stopped. Its only app change since v0.15.0 was in Settings, which
doesn't run at startup. The start without the Baseline Profile was 25% slower
too. The runner type, image and 2 emulator cores were the same, so the
difference came from the shared GitHub machine the job landed on.

Stopping a release for that kind of noise means rerunning the job until a
faster machine comes up, which tells nobody anything.

## Decision

On the reference emulator, against the baseline for the same runner and image:
- **More than 15% slower:** a warning on the run page and in the run summary.
  The release goes on to the approval step.
- **More than 50% slower:** the release stops, as before. Noise doesn't reach
  that; a real startup regression does.

Everything else in ADR 0028 stays: the baseline still names its runner and
image, jank is still only recorded, and the absolute 1.5 s and 5% budgets are
still checked on a low-end phone in Firebase Test Lab before a Play promotion.

## Consequences

- Releases no longer stop because of a slower runner.
- A regression between 15% and 50% doesn't stop a release on its own. The
  warning, a warning that repeats on later tags, and the Firebase Test Lab run
  before each Play promotion are what catch it.
