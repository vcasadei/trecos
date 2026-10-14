# 0028. Reference emulator and the performance gate

- **Status:** Accepted
- **Date:** 2026-10-13
- **Change:** `device-test-suite` (design decisions 7 to 9; spec device-testing, "Reference emulator and performance gate")

## Context

`trecos-v1` asked for a physical reference phone: Android 9, 2 GB. Each release
had to reach a median cold start of 1.5 s and fewer than 5% janky frames on it,
and the encryption round trips had to pass on it. The developer has no such
phone.

An emulator with the same Android version, memory and 2 cores gives:
- a 1,792 ms cold start;
- 97.7% janky frames, because it draws every frame in software.

So the emulator can't judge the absolute budgets. Its timings also vary with
the host's hardware.

## Decision

- **Reference emulator:** Android 9 (API 28), 2 GB, 2 cores, the `google_apis`
  x86_64 image. It replaces the physical phone for the release gate.
- **Emulator gate, regressions only:** every release tag measures the cold
  start there. The release is not published when the cold start is more than
  15% slower than `benchmark/reference-baseline.json`.
  - The baseline names its runner type and system image, and a run on another
    runner or image fails until the baseline is recorded again on purpose
    (`release.yml` by hand, "Record a new performance baseline").
  - Jank is recorded but doesn't gate.
- **Absolute budgets on a real phone:** before a build is promoted on Google
  Play, the same benchmark runs on a low-end physical phone in Firebase Test
  Lab. `scripts/check-benchmark.py --budget` must pass: 1.5 s, under 5% janky
  frames.
- **Encryption on Android 9:** `EncryptionDeviceTest` checks the
  Keystore-wrapped key and the re-encryption on the reference emulator in every
  device run. The Drive round trips run on Android 16 only, because the older
  emulator images ship a Google Play services from 2020 that can't authorize
  Drive access and can't update.

## Consequences

- Releases can't silently get slower on low-end devices, without anyone
  owning a 2 GB phone.
- The absolute numbers come from real hardware, at the cost of a manual
  Firebase Test Lab run before each Play promotion.
- A new runner or emulator image needs a new baseline before the next release.
