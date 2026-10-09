# Release

Until 1.0 every release is a signed APK on GitHub Releases, for sideloading.
1.0 goes to Google Play signed with the same key, so sideloaded installs keep
updating (design D22). Local builds are debug builds and need no secret.

> **Status:** set up on 2026-10-09. The age key is backed up offline (printed
> and on a NAS), the encrypted file is in `vcasadei/trecos-signing` and in Google
> Drive, and the first signed release is `v0.12.0`. The secret values below are
> placeholders.

Release certificate fingerprints (public):

| | |
|---|---|
| SHA-256 | `2F:CE:99:B6:6D:22:8B:E5:D9:F9:B0:6F:4A:C7:AD:04:05:53:1E:0D:C4:A1:11:29:85:78:78:AC:0F:DD:78:65` |
| SHA-1 (for the OAuth client) | `90:3F:05:D5:C2:7F:73:FA:5D:01:07:0C:F2:20:4F:5A:34:C6:6E:44` |

## The signing key

| Piece | Where | Who can read it |
|---|---|---|
| `release-signing.sops.yaml` (keystore + passwords, encrypted) | Private repo `vcasadei/trecos-signing`, and a Google Drive folder | Anyone with the age key |
| age identity (decrypts the file) | `~/.config/sops/age/keys.txt` (mode 600) on the dev machine, plus an offline backup (printed and on a USB drive) | The developer only |
| CI copy | GitHub Actions secrets, below | Only the release workflow |

The age key is **never** stored next to the encrypted file: not in either
repository and not in the Drive folder. Together they are the plaintext key.

## CI secrets

The release workflow reads only these four secrets. It decodes the keystore
into `$RUNNER_TEMP` and passes its path as `TRECOS_KEYSTORE_FILE`; Gradle's
release `signingConfig` reads only `TRECOS_KEYSTORE_FILE`,
`TRECOS_KEYSTORE_PASSWORD`, `TRECOS_KEY_ALIAS` and `TRECOS_KEY_PASSWORD`.

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

## Google Drive sign-in (OAuth client)

Sync (release 0.11, `FEATURE_DRIVE_SYNC`, on by default) signs in with Google Identity's
`AuthorizationClient`. Android OAuth clients have **no client secret**: Google
recognises the app by its package name and signing certificate. Nothing is
added to the repository.

1. In the [Google Cloud console](https://console.cloud.google.com/), create a
   project (for example "Trecos") and enable the **Google Drive API**.
2. **OAuth consent screen**: External, app name "Trecos", support e-mail,
   developer contact, the privacy policy URL (`PRIVACY.md` on GitHub until the
   site exists), and the scopes `https://www.googleapis.com/auth/drive.file`
   and `https://www.googleapis.com/auth/drive.appdata`. Both are non-sensitive
   scopes, so no Google verification is needed. While in "Testing", add your
   account as a test user.
3. **Credentials > Create OAuth client ID > Android**, twice:
   - package `app.trecos` with the **release** certificate's SHA-1:
     `keytool -list -v -keystore <release.jks> -alias <alias>` (from the
     decrypted SOPS file);
   - package `app.trecos` with the **debug** certificate's SHA-1 of the
     machine that builds debug APKs:
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`,
     or `./gradlew signingReport`.
   Once enrolled in Play App Signing (task 8.7), add a third client with the
   **Play app signing** certificate's SHA-1 from the Play Console.
4. Verify on a debug build: `./gradlew :app:installDebug`. Then go to Settings >
   Sync & backup > Sync > Connect Google Drive, pick the account and allow
   access. A "Trecos" folder appears in My Drive, and Sync shows "Last sync".

If Google shows "DEVELOPER_ERROR" or the connect screen closes at once, the
SHA-1 or the package name of the client doesn't match the installed APK.

## Performance pass (release gate)

Every release is measured on the **reference phone**: Android 9, 2 GB of RAM
(spec "Performance on low-end phones", task 14.8). It must reach:

- a median cold start to the interactive Home screen of **1.5 s or less** over
  10 runs, with 1,000 items stored;
- **janky frames under 5%** while scrolling those items.

1. Connect the phone with USB debugging on. The benchmark build is profileable,
   not debuggable, so no root is needed on a real phone.
2. Run `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`. The
   benchmarks store 1,000 items through the benchmark-only
   `BenchmarkSeedReceiver`, which is disabled in every other build.
3. Gate the release:
   `scripts/check-benchmark.py baselineprofile/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/*/app.trecos.baselineprofile-benchmarkData.json`.
   The script fails, and the release is not published, when the median cold
   start (time to full display, reported once the Home list has loaded) is over
   1.5 s, or when the 95th percentile of frame overruns is late.

Regenerate the Baseline Profile after UI changes with
`./gradlew :app:generateBaselineProfile` on a rooted emulator (`adb root` on a
Google APIs image). On an emulator, add
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`.
Emulator numbers are not valid for the gate, and the software renderer doesn't
report frame timings.

## Publishing a release

`.github/workflows/release.yml` runs when a `v*` tag is pushed:

| Step | What it does |
|---|---|
| Decode the keystore | `TRECOS_KEYSTORE_BASE64` into `$RUNNER_TEMP/release.p12`, removed at the end |
| Build | `./gradlew assembleRelease -Ptrecos.version=<tag without the v>` with the four variables: one APK per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) plus a universal one |
| Check | Debug logs stripped; `apksigner verify --print-certs` on every APK |
| Publish | `trecos-<version>-<abi>.apk`, `SHA256SUMS` and the certificate fingerprint in the notes, as a pre-release |

```sh
git tag v0.2.0 && git push origin v0.2.0
```

### Version

The tag sets the version; nothing is bumped by hand. `v1.2.3` gives
`versionName` 1.2.3 and `versionCode` 10203 (major × 10000 + minor × 100 +
patch), so every release has a higher code than the one before, as Google
Play requires. The tag must be three numbers, with minor and patch below 100;
anything else (for example `v1.2` or `v1.2.3-rc1`) stops the build. A build
without `-Ptrecos.version`, such as a local one, is `0.0.0-dev` with
`versionCode` 1.

The workflow has not run yet: it needs the secrets (tasks 2.20-2.22). The
signing path itself was tested locally with a throwaway key: the release build
signs when `TRECOS_KEYSTORE_FILE` and the three other variables are set, and
stays unsigned otherwise.
