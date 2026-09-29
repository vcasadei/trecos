# 0011. App lock, optional profile, optional encryption

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D16, D17; spec app-lock, encryption)

## Context

The draft asked for a Trecos password and login; the user also wants database encryption, with recovery on a new phone.

## Decision

No Trecos password. An optional **app lock** uses the phone's own biometrics or PIN. The **profile** (name, e-mail) is fully optional and only labels sync history. **Database encryption** is a setting, off by default, and needs a connected Google account: the key is kept in Drive's hidden app folder (plus a Keystore-wrapped local copy), and Drive snapshots are encrypted with it. Photos are not encrypted but are synced.

## Consequences

Losing the Google account means losing encrypted data; a compromised Google account is out of scope.
