# 0019. Two-level categories, several per item, tags and suggestions

- **Status:** Accepted
- **Date:** 2026-09-28
- **Change:** `trecos-v1` (design D7; spec categories-and-tags)

## Context

Maker inventories have many similar parts (cables and adapters by connector), and a single category can't describe a USB-C to USB-A cable.

## Decision

Categories have **two levels**, from an expanded built-in list (about 20 groups, about 95 subcategories, including Cables and Adapters with mirrored connector subcategories). An item can have **one or more** categories; the first is the main one. Tags are the user's free-text system, per house. **Suggestions** from name and description appear as tap-to-add chips, never added automatically, from an offline keyword dictionary (English and Portuguese) plus learning from the user's own choices.

## Consequences

The category picker needs search.
