# Encryption

Optional database encryption with a key the user can recover on a new phone
(design D16). The code is in `app/src/main/kotlin/app/trecos/crypto/`.
Encryption is behind the `FEATURE_ENCRYPTION` build flag, on by default since
2026-10-09 (build with `-Ptrecos.encryption=false` to switch it off). The
setting itself is off by default, is labelled "Experimental" until design D19's
criteria are met, and needs Google Drive sync to be connected.

## The key

- Turning encryption on creates a random 256-bit key (`SecureRandom`).
- **On the phone**: the key is stored in `noBackupFiles/crypto/key.bin`,
  wrapped with AES-GCM by a key that never leaves the Android Keystore
  (`AndroidKeystoreWrapper`). It isn't included in Android backups.
- **For recovery**: the key is uploaded to Drive's hidden app folder
  (`appDataFolder`, `drive.appdata` scope) as `trecos-key.bin`. It is
  invisible in Drive and readable only by Trecos signed in to the same account.
  The upload happens **before** anything is encrypted, so the key can always
  be recovered.
- SQLCipher opens the database with the key's hex text as its passphrase.
- The key is never shown, logged or exported. Tests check the logs and
  backup files for it.

## Re-encrypting the database

The database always runs on SQLCipher (design D3); only its key changes. The
file is re-encrypted in two halves (`EncryptionSwap`), so an interruption
anywhere leaves a usable database:

1. **Prepare**, while the app runs:
   - write the phase marker `exporting`;
   - use `sqlcipher_export` to write `trecos.db.next`, with the new key or
     none, and set its `user_version`;
   - stage the new key in `key.next` (or a "plain" marker);
   - write the phase marker `ready`;
   - restart the app at once, so no edit can land after the export.
2. **Finish**, at the next start, before anything opens the database:
   - `exporting`: delete the half-written file and the staged key. The old
     database stays in use, unchanged and unencrypted.
   - `ready`: delete the old file's journals, move the old file aside, move the
     new one in, and record `swapped`.
   - `swapped`: make the staged key current, and delete the old file and the
     marker.

Each step is idempotent, and `EncryptionSwapTest` interrupts before, during
and after the swap.

## Drive copies

With encryption on, every commit file uploaded by sync is
`TRCE1 || IV || AES-GCM(ciphertext + tag)` under the same key
(`SnapshotCipher`). Refs hold only commit ids and stay plain. **Photos are
never encrypted**; they still sync and back up. The local commit cache keeps
the files as uploaded, still encrypted.

Reading tries every key the phone knows: the current or staged key, the key
found in Drive for this session, and the key retired when encryption was turned
off, which is kept only to read older commits.

## Recovery on a new phone

When the user connects Google Drive, the app looks for `trecos-key.bin`. If it
is there, the key is used for this session: the restore decrypts the latest
commits. Then the new phone's database is encrypted with the same key, and the
app restarts. Changes that never reached Drive can't be recovered.

## Turning it off

After the user confirms, the app:

- prepares an unencrypted database;
- keeps the old key only to read older Drive commits;
- deletes the key from Drive and restarts.

Later commits are uploaded unencrypted. While encryption is on, disconnecting
Google Drive is refused, because the key's recovery copy depends on that
account.

## Backups

Export files are never encrypted. With encryption on, the app warns before
writing one.

## Tests

- `EncryptionSwapTest` (JVM): the swap state machine with fault injection, the
  key at rest and the snapshot cipher.
- `EncryptionScenariosTest` (Robolectric): every spec scenario through the UI,
  with a faked export and restart.
- `EncryptionDeviceTest` (device): the real `sqlcipher_export`, the swap,
  opening with the Keystore-wrapped key, full-text search, and decrypting back.
