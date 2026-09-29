# 0003. QR identity is its own per-house unique field

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D4, D9; spec qr-codes)

## Context

The draft used the name as the QR payload, but names aren't unique and renames would break printed labels.

## Decision

`qrCode` is a **separate field**, filled from the name at creation and editable. It stays the same when the record is renamed. Names may repeat. Codes are unique **per house** (0007). Scanning an unknown code offers to create an item or container with the name pre-filled.

## Consequences

A partial unique index on `(houseId, qrCode)`. Codes made by other programs work as raw strings.
