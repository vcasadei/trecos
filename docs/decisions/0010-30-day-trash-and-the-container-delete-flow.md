# 0010. 30-day trash and the container-delete flow

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (spec trash)

## Context

Deletes should be recoverable, and deleting a container needs a way to keep some of its contents.

## Decision

Every delete asks for confirmation, offers **Undo**, and moves the record to a **per-house trash for 30 days**; restore returns it to its original place, or asks if that place is gone; photos are freed on purge. Deleting a container offers: send it and everything inside to the trash, or **choose what to keep** with checkboxes, moving selections to one or several destinations (any container, the parent, or a new container at the same level) before "Finish" sends the rest to the trash. A checked sub-container moves whole.

## Consequences

Deleting a house is permanent (typed confirmation) and suggests an export first.
