# Release

Every release is a signed APK on GitHub Releases, for sideloading, and an App
Bundle for the Google Play closed test; 1.0 goes to Play production. Play signs
with the same key, so sideloaded installs keep updating (design D22). Local builds are debug builds and need no secret.

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

## Google Play signing

Set up on 2026-10-16 (trecos-v1 task 8.7). Play App Signing holds the **same
release key** as GitHub Releases, uploaded with PEPK instead of a key Google
generates, so sideloaded installs can update from Play. The Play Console shows
the app signing key's SHA-256 as `2F:CE:99:…:DD:78:65`, as above.

Builds for Play are signed with a separate **upload key** (RSA 4096, alias
`upload`). It is stored like the release key: as the `upload_*` fields of
`release-signing.sops.yaml` and as four secrets in the `release` Environment.

| Secret | Contents |
|---|---|
| `TRECOS_UPLOAD_KEYSTORE_BASE64` | The PKCS12 upload keystore, base64 |
| `TRECOS_UPLOAD_KEYSTORE_PASSWORD` | Its store password |
| `TRECOS_UPLOAD_KEY_ALIAS` | `upload` |
| `TRECOS_UPLOAD_KEY_PASSWORD` | Its key password |

Each release run builds `trecos-<version>.aab` with `-Ptrecos.bundle` (which
turns off the per-ABI APK splits that bundles can't use), signs it with the
upload key and keeps it as the `play-bundle` workflow artifact for 90 days, with
`upload-fingerprint.txt`. Download it from the run's page and upload it in the
Play Console.

If the upload key leaks or is lost, ask for an upload key reset in the Play
Console (App signing, "Request upload key reset"); the release key is not
affected.

How it was set up, for the record:

1. `~/play-upload-key.sh` on the dev machine created the upload key, added the
   `upload_*` fields to the encrypted file and set the four secrets, and wrote
   the public `~/upload_certificate.pem`.
2. In Play Console, App signing, "Change key", "Export and upload a key from
   Java keystore", the developer downloaded `encryption_public_key.pem` and
   copied it to the dev machine with `scp`.
3. `~/play-pepk.sh` checked the keystore's fingerprint against the one above
   and ran PEPK (`--rsa-aes-encryption --include-cert`), writing a zip only
   Google can decrypt.
4. The zip and `upload_certificate.pem` were uploaded in the same dialog, and
   the Console then showed the release key's SHA-256.

Verified on 2026-10-16: a phone with the GitHub `trecos-0.15.4-universal.apk`
updated to 0.15.5 from the Play internal test, and its data was kept.

## Google Play closed test

Started on 2026-10-16 with 0.15.5 (launch-features task 1.2, trecos-v1 8.8).
Production is 1.0; until then Play has two tracks:

| Track | Who | Review | Use |
|---|---|---|---|
| Internal testing | The developer's own e-mail list (up to 100) | None; live in minutes | First check of every build |
| Closed testing | The `trecos-testers@googlegroups.com` Google Group | Full review; days the first time | Real testers, and the production-access requirement |

**Play's requirement** (checked 2026-10-16, Play Console Help answer 14151465):
personal developer accounts created after 2023-11-13 need **at least 12
testers opted in continuously for 14 days** on a closed test before they can
apply for production. Someone who leaves and rejoins starts their 14 days over,
so recruit 15 to 20.

### Each release

1. Push the `v*` tag and approve "Review deployments" (see Publishing a release).
2. Download the `play-bundle` artifact from the run (`trecos-<version>.aab`).
3. Play Console, Test and release, Testing, **Internal testing**, Create new
   release: upload the bundle; App integrity must say it uses our key; add
   release notes in `en-US` and `pt-BR`; Start rollout. Install it on a phone
   from the internal test link.
4. Promote the same release to **Closed testing** (Promote release).

The bundle has third-party native code (SQLCipher) without debug symbols;
Play's warning about missing native symbols can be ignored.

### Testers

Testers join through **<https://trecos.app/testers/>** (English) or
**<https://trecos.app/pt/testar/>** (Portuguese), built from
`site/content/testers.md` and `testar.md`. The pages give three steps: join the
`trecos-testers` Google Group (anyone can join, members are hidden), become a
tester at <https://play.google.com/apps/testing/app.trecos>, and install from the
Play Store. The closed track's Testers tab lists that group, so no e-mail
addresses are typed into the Console.

The invitation sent to testers (WhatsApp or e-mail):

> Oi! Estou lançando o Trecos, um app gratuito para Android que guarda o que
> você tem em casa e onde cada coisa está guardada: caixas, prateleiras,
> cômodos, com etiquetas QR. Funciona sem internet, sem conta e sem anúncios.
> Preciso de pessoas para testar antes do lançamento. Os passos estão aqui (leva
> 2 minutos): https://trecos.app/pt/testar/ — É só usar a conta Google do seu
> celular Android e ficar no teste por pelo menos 14 dias (não precisa usar todo
> dia, só não sair). Se algo der errado ou ficar confuso, me conte. Obrigado!

> Hi! I'm launching Trecos, a free Android app that keeps track of what you own
> and where it's stored: boxes, shelves, rooms, with QR labels. It works
> offline, with no account and no ads. I need testers before launch. The steps
> are here (takes 2 minutes): https://trecos.app/testers/ — Use the Google
> account on your Android phone and stay in the test for at least 14 days (no
> need to use it every day, just don't leave). If anything breaks or is
> confusing, tell me. Thank you!

**What to try** (also on the testers pages):

- Create a home, a few rooms and boxes, and put boxes inside boxes.
- Add items with a photo, a price and a category.
- Search for something, and use the filters.
- Make a QR label for a box, print or share it, and scan it back.
- Try the dark theme, and switch between Portuguese and English in Settings.

### Store listing and declarations

The listing (English, and a Portuguese translation), graphics and declarations
were filled in on 2026-10-16:

- Name "Trecos: Home Inventory" / "Trecos: Inventário da Casa"; category
  Productivity; website <https://trecos.app>.
- Icon 512×512, feature graphic 1024×500 and six 1080×1920 phone screenshots
  per language, taken on the Android 9 emulator with demo data.
- Privacy policy <https://trecos.app/privacy/>; no ads; no advertising ID; all
  functionality without special access; target audience 18 and over.
- Data safety: data is collected only through the optional Drive sync to the
  user's own Drive, never shared, encrypted in transit, optional, for app
  functionality: name, e-mail, address, photos, other user-generated content
  and an app-generated device id. Delete-data URL
  <https://trecos.app/delete-data/>. It must stay in step with `PRIVACY.md`.

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

The goal stays the same (spec "Performance on low-end phones"): on a 2 GB
Android 9 phone, a median cold start to the interactive Home screen of **1.5 s
or less** over 10 runs with 1,000 items stored, and **janky frames under 5%**
while scrolling them. Two checks hold every release to it (ADR 0028, 0029):

**1. Every tag, on the reference emulator.** The `benchmark` job of
`release.yml` boots Android 9, 2 GB, 2 cores (`google_apis` x86_64) and runs
`scripts/release-benchmark.sh check`:
- it runs `StartupBenchmark` and `ScrollJankTest`;
- more than **15% slower** than `benchmark/reference-baseline.json` shows a
  warning on the run page and in the summary, and the release goes on: shared
  runners alone vary by about 25%;
- the release is not published when the median cold start is more than **50%
  slower**;
- jank is shown in the run summary but doesn't gate, because the emulator's
  software GPU makes almost every frame janky.

The baseline names the runner type and the image's build fingerprint. A run on
another runner or image fails until the baseline is recorded again, on
purpose:
1. Run **Release** by hand (Actions > Release > Run workflow) with "Record a
   new performance baseline" checked. A manual run never publishes.
2. Download `benchmark-results` from the run and commit its
   `benchmark/reference-baseline.json` in a pull request.

A manual run without that box is a dry run of the gate. Check "Also run the
release device tier" to include the 36 cells.

**2. Before a Play promotion, on a real phone.** Run the same benchmark on a
low-end physical phone (Android 9 or the oldest available, 2 to 3 GB) in
Firebase Test Lab:

```sh
./gradlew :app:assembleBenchmarkRelease :baselineprofile:assembleBenchmarkRelease
gcloud firebase test android run --type instrumentation \
  --app app/build/outputs/apk/benchmarkRelease/app-x86_64-benchmarkRelease.apk \
  --test baselineprofile/build/outputs/apk/benchmarkRelease/baselineprofile-benchmarkRelease.apk \
  --device model=<low-end model>,version=28 \
  --environment-variables additionalTestOutputDir=/sdcard/Android/media/app.trecos.baselineprofile/results \
  --directories-to-pull /sdcard/Android/media/app.trecos.baselineprofile
python3 scripts/check-benchmark.py --budget <pulled folder>/results/*benchmarkData.json
```

This route hasn't run yet; the first run is task 8.5 of `device-test-suite`,
before the first Play promotion.

Use the APK for the phone's ABI (`arm64-v8a` on most phones) and a model
from `gcloud firebase test android models list`. Promote the build only when
`--budget` passes. On a phone of your own, `./gradlew
:baselineprofile:connectedBenchmarkReleaseAndroidTest` followed by the same
`--budget` check does the same.

Regenerate the Baseline Profile after UI changes with
`./gradlew :app:generateBaselineProfile` on a rooted emulator (`adb root` on a
Google APIs image). On an emulator, add
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`.

## Publishing a release

`.github/workflows/release.yml` runs when a `v*` tag is pushed. Two gates run
first, and publishing waits for both:
- **benchmark:** the cold start on the reference emulator, against the baseline
  (above);
- **device-tests:** the `release` tier of `device-tests.yml`, meaning all 36
  emulator cells plus the Drive job with the Google test account
  (`docs/dev/device-tests.md`).

Then the `release` job, in the `release` environment:

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
