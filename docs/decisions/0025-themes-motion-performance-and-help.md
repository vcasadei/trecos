# 0025. Themes, motion, performance and help

- **Status:** Accepted
- **Date:** 2026-09-28
- **Change:** `trecos-v1` (design D10, D18, D20; spec app-shell, help-and-support)

## Context

The draft asked for dark, white and off-white skins, snappy animations, good performance on old phones, a FAQ and a rate button.

## Decision

Themes: Follow system (default), **White** (off-white) and **Dark** (pure black). No Material You. Animations of 150-250 ms that respect "Remove animations". Targets: smooth on 2 GB RAM phones, cold start of 1.5 s or less, smooth 1,000-item lists, Baseline Profiles. A 10-question FAQ, a Rate button, **one** automatic review prompt ever (after 14 days, 20 items and 5 sessions), and Contact by e-mail to hello@trecos.app.

## Consequences

The site and e-mail forwarding for trecos.app are set up at 1.0.
