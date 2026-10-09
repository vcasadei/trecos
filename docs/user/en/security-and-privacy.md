# Security and privacy

## App lock

Settings > **Security** > **App lock** asks for your phone's own fingerprint,
face, PIN, pattern or password to open Trecos. There is no separate Trecos
password. The phone needs a screen lock first.

- **Lock after**: immediately, 1 minute (default), 5 or 15 minutes in the
  background. Trecos always asks when it starts.
- While Trecos is locked, only an **Unlock** button shows, and the recent-apps
  preview hides your inventory.
- If you remove the phone's screen lock later, Trecos turns the app lock off and
  tells you.

## Profile

Settings > **Security** > **Profile (optional)** holds a name and an e-mail.
They only label your sync records. Nothing in Trecos needs them. You can
change or delete them at any time, and they are never written to logs.

## Encryption

Settings > **Security** > **Encrypt data** encrypts your inventory on this
phone and its copies in Google Drive. It needs sync connected: the key is kept
in your Google account so you can recover your data on a new phone.

Encryption is **experimental** for now: it has been tested on few phones.
Export a backup before turning it on (Settings > Sync & backup > Backup).

- Trecos restarts to finish, and your data stays intact if it is interrupted.
- Photos aren't encrypted, and backup files never are (Trecos warns you before
  exporting).
- To disconnect Google Drive, turn encryption off first.
- If you lose access to your Google account, encrypted data can't be recovered.

## What Trecos never does

No ads, no analytics, no crash reporting, no tracking. Without sync, Trecos
sends nothing off your phone. See the [privacy policy](../../../PRIVACY.md).
