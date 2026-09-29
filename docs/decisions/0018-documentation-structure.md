# 0018. Documentation structure

- **Status:** Accepted
- **Date:** 2026-09-28
- **Change:** `trecos-v1` (tasks.md (header, 1.5, 1.6))

## Context

The repository needs comprehensive documentation from the start.

## Decision

Root: README, LICENSE, COMMERCIAL, CONTRIBUTING, CLA, CODE_OF_CONDUCT, SECURITY, PRIVACY, CHANGELOG. `docs/product` (vision, glossary, roadmap), `docs/architecture`, `docs/decisions` (these ADRs), `docs/dev`, and `docs/user/en` plus `docs/user/pt-BR`. User docs are bilingual; everything else is in English.

## Consequences

The in-app FAQ is sourced from `docs/user/*/faq.md`.
