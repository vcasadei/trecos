# 0009. Display-only currency

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D12; spec settings)

## Context

Items need a currency symbol, but conversion would need online rates.

## Decision

A **single currency setting**, defaulting to the phone's region, that relabels amounts **without converting** them, with a warning when changed. Number format follows the app language.

## Consequences

Amounts are stored as integer minor units.
