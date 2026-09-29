# 0008. No metric setting; unit labels on number fields

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (spec custom-fields)

## Context

The draft offered a "metric" preference with no clear use.

## Decision

Drop the metric/imperial setting from v1. Custom number fields get a free-text **unit label** (cm, GB, W) instead, with no conversion.

## Consequences

Nothing to convert, nothing to localise beyond labels.
