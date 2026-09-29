# 0015. Three tabs: Search | Home | Settings

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D10; spec app-shell, search)

## Context

The draft proposed four tabs (Home, Items, Settings and one more) and a search icon on every screen.

## Decision

**Three tabs in the order Search | Home | Settings.** Items is merged into Search (an empty query lists all items). No persistent search icon; a container's menu offers "Search in this container". The bar floats and is rounded; unselected tabs show only a label, the selected one a raised circle with its icon that slides between tabs (about 200 ms). The "+" button sits above the bar on the right.

## Consequences

One custom composable draws the bar.
