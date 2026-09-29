# 0014. Sync as per-house snapshots in a visible Drive folder

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D14, D15; spec sync, backup)

## Context

The draft asked for git-like sync with a conflict screen only on real conflicts.

## Decision

A visible `My Drive/Trecos/` folder with, per house, gzipped JSON Lines snapshots (`commits/`), one ref file per device that only that device writes (`refs/`), and content-addressed photos (`objects/`). Merges are **three-way per field** against the last synced commit; the conflict screen appears only when the same field changed on both sides or on edit-versus-delete. The trash syncs too. A **local export/import `.zip`** in the same format ships before sync. The schedule is every day (default), 5, 15 or 30 days, or never, with "Photos only on Wi-Fi" (on) and "Sync now".

## Consequences

No write races on Drive; the merge engine is plain Kotlin and exhaustively unit-tested.
