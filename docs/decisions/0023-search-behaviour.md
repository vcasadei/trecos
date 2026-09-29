# 0023. Search behaviour

- **Status:** Accepted
- **Date:** 2026-09-28
- **Change:** `trecos-v1` (design D6; spec search)

## Context

The draft listed search scope, filters and sorting.

## Decision

Accent- and case-insensitive, partial matching as you type (debounced). Match in Name + Description (default), Name or Description, where Name also covers brand, model, serial and QR. Scope Items (default), Containers or both. Filters by house, category (including subcategories) and tags: any within a filter, all across filters. Sort by name (default), date added or unit price, unpriced last, with a direction toggle. Live count. Filters are remembered; search text history is not.

## Consequences

FTS4 with diacritics removed.
