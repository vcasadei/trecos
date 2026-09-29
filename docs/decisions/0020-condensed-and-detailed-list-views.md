# 0020. Condensed and detailed list views

- **Status:** Accepted
- **Date:** 2026-09-28
- **Change:** `trecos-v1` (design D11; spec app-shell)

## Context

The draft described a condensed list and a configurable detailed one.

## Decision

A toggle in every list's top bar switches between **condensed** (48 px thumbnail, one line) and **detailed** (96 px thumbnail, name, two lines of description, quantity, unit price, and up to three extra fields chosen in Settings). The choice is app-wide and remembered. Empty fields are hidden. **All text truncates** with an ellipsis, and long unbroken words wrap. No grid view in v1.

## Consequences

Stress fixtures (long names, long serials, deep paths, Portuguese) are part of the screenshot tests.
