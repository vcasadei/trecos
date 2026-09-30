# Release

Until 1.0 every release is a signed APK on GitHub Releases, for sideloading.
1.0 goes to Google Play signed with the same key, so sideloaded installs keep
updating (design D22). Local builds are debug builds and need no secret.

> **Status (0.2 in progress):** the signing key, the CI secrets and the release
> workflow are not set up yet (tasks 2.19-2.23). The values below are placeholders.

## The signing key

| Piece | Where | Who can read it |
|---|---|---|
| `release-signing.sops.yaml` (keystore + passwords, encrypted) | Private repo `vcasadei/trecos-signing`, and a Google Drive folder | Anyone with the age key |
| age identity (decrypts the file) | `~/.config/sops/age/keys.txt` (mode 600) on the dev machine, plus an offline backup (printed and on a USB drive) | The developer only |
| CI copy | GitHub Actions secrets, below | Only the release workflow |

The age key is **never** stored next to the encrypted file: not in either
repository and not in the Drive folder. Together they are the plaintext key.

## CI secrets

The release workflow reads only these four environment variables; Gradle's
release `signingConfig` reads nothing else.

| Secret | Contents | Placeholder |
|---|---|---|
| `TRECOS_KEYSTORE_BASE64` | The PKCS12 keystore, base64 | `MIIK…(base64)…` |
| `TRECOS_KEYSTORE_PASSWORD` | Store password | `store-password-here` |
| `TRECOS_KEY_ALIAS` | Key alias | `trecos` |
| `TRECOS_KEY_PASSWORD` | Key password | `key-password-here` |

They are set from the decrypted file through stdin, so no value touches the
disk or the shell history:

```sh
sops -d --extract '["store_password"]' release-signing.sops.yaml | gh secret set TRECOS_KEYSTORE_PASSWORD
```

Since the repository is public, the secrets live in a `release` Environment
that only `v*` tags can deploy to, with the developer as required reviewer.

## Recovering the key

Use this when the dev machine is lost or replaced.

1. Install SOPS and age (see [setup-headless-linux.md](setup-headless-linux.md)).
2. Restore the age identity from the offline backup:
   - from the USB drive: copy `keys.txt` to `~/.config/sops/age/keys.txt`;
   - from the printout: type the `AGE-SECRET-KEY-1…` line into that file.
3. `chmod 600 ~/.config/sops/age/keys.txt`.
4. Get the encrypted file from **either** copy: clone `vcasadei/trecos-signing`,
   or download `release-signing.sops.yaml` from the Google Drive folder.
5. Check it decrypts, without writing the plaintext anywhere: `sops -d release-signing.sops.yaml > /dev/null && echo OK`.
6. If the CI secrets were lost too, set them again as shown above.

If the age key itself is lost but the CI secrets still work, generate a new age
key and re-encrypt: `sops updatekeys release-signing.sops.yaml` after adding
the new recipient to `.sops.yaml`.

## Rotation

On compromise only. Android 9+ supports APK Signature Scheme v3 key rotation
(`apksigner rotate`), so a leaked key can be replaced without breaking updates.

## Publishing a release

Planned for task 2.23: pushing a `v*` tag builds per-ABI and universal APKs,
checks them with `apksigner verify --print-certs`, and publishes them on GitHub
Releases with a `SHA256SUMS` file and the certificate fingerprint in the notes.
