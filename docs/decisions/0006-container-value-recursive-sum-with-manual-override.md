# 0006. Container value: recursive sum with manual override

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D5; spec places)

## Context

The draft asked for an auto-calculated container value the user can override.

## Decision

The automatic value is the **recursive** sum of every item below the container. A manual override replaces the container's value **and counts upward** into its parents. The screen shows whether a value is automatic or manual, offers "clear override", and notes how many items have no price (they count as 0).

## Consequences

Values are folded in memory from one query per house, with no cached aggregates.
