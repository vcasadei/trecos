# 0012. Photo size and thumbnails

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D8; spec photos)

## Context

Photos should be small, not high resolution, and lists must stay fast.

## Decision

Store photos at **1920 px on the long edge, WebP quality 80** (about 150-300 KB), with **320 px square thumbnails** for lists. Location metadata is stripped and the original discarded. Thumbnails aren't synced; each device rebuilds them. Up to **3 photos** per item, container or house.

## Consequences

No camera or storage permission: the system camera and the Photo Picker are used.
