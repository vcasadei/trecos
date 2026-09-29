# 0004. Languages: English and Portuguese (Brazil)

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D12; spec app-shell)

## Context

The developer and first users are Brazilian; English widens the audience.

## Decision

Launch in **English and Portuguese (Brazil)**. All app text lives in translatable resources from 0.1. An in-app language picker covers Android 9-12. Built-in categories and the FAQ are translated; user-created categories are not. Currency is a separate setting (0009).

## Consequences

Every screen is screenshot-tested in both languages; Portuguese strings run longer.
