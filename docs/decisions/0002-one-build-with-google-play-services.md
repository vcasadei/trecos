# 0002. One build with Google Play services

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design Context, Risks)

## Context

A FOSS flavor would need alternatives for QR scanning, Drive, billing and review, and F-Droid is not a goal (0001).

## Decision

Ship a **single build** that may use Google Play services. No `foss` flavor.

## Consequences

On devices without Play services (Huawei, custom ROMs), scanning, sign-in, sync, tips and review are unavailable; everything else works.
