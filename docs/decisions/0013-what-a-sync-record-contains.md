# 0013. What a sync record contains

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D14; spec sync)

## Context

The draft asked for a timestamp, user, location and device id for each sync.

## Decision

Each sync records the timestamp, the Google account (name and e-mail), the device (a random install id plus a renamable friendly name) and a change summary. **No location.** Settings > Sync shows the history.

## Consequences

No location permission is ever requested.
