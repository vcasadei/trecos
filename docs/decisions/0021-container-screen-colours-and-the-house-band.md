# 0021. Container screen, colours and the house band

- **Status:** Accepted
- **Date:** 2026-09-28
- **Change:** `trecos-v1` (design D10, D11; spec places)

## Context

The draft described the container screen layout; the user also wants colours to tell containers and houses apart.

## Decision

Fixed top bar (back, name, edit, menu), then a header that scrolls away (photo carousel or icon, expandable description, tappable breadcrumb with middle collapse, QR, value), then "Containers (n)" and "Items (n)". The "+" is a speed dial with Item and Container. Long-press starts multi-select. Containers can take one of about **12 pastel colours** (a tint and a row stripe), inherited by sub-containers. Houses show their colour as a **status-bar band** (at two or more houses, with a setting) and a house pill.

## Consequences

Colour is never the only way to tell things apart; every pairing is contrast-tested.
