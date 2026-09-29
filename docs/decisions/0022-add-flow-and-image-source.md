# 0022. Add flow and image source

- **Status:** Accepted
- **Date:** 2026-09-28
- **Change:** `trecos-v1` (spec items, photos, settings)

## Context

The draft wanted a choice between starting with the camera or the form, and an image-source preference asked on first use.

## Decision

A setting for **Form first** (default) or **Photo first**. On first use a Camera / Gallery prompt with "Remember my choice" checked. The form shows name, categories (with suggestions), quantity and price; "More fields" reveals the rest. **Save** and **Save + new** (keeping the container and categories). Scanning a QR fills the code, and the name if empty.

## Consequences

No camera or storage permission is needed.
