# Device tests

The device suite runs Trecos on Android emulators with
[Maestro](https://github.com/mobile-dev-inc/maestro) flows. It checks every
screen and feature on real Android runtimes, from Android 9 to Android 16. The
Robolectric tests in `app/src/test` stay the fast first line. Flows check
whole journeys and the parts only a real device has: system pickers, account
settings, the fingerprint lock, SQLCipher and real memory limits.

| Item | Where |
|---|---|
| Flows | `maestro/flows/<area>/*.yaml`, one area per `trecos-v1` capability |
| Shared steps | `maestro/common/*.yaml` |
| Plan and requirements | `openspec/changes/device-test-suite/` |
| Maestro version | `cli-2.11.0` (pinned) |

## Local setup

1. Install Maestro from its GitHub release and check the archive:

   ```bash
   gh release download cli-2.11.0 -R mobile-dev-inc/maestro -p maestro.zip
   echo "5384593cb4e7a106489e75a821d157dd43f4e438df6bc308b72e82c685e1283a  maestro.zip" | sha256sum -c
   unzip -q maestro.zip -d ~/.local/opt
   export PATH="$HOME/.local/opt/maestro/bin:$PATH"
   maestro --version   # 2.11.0
   ```

2. Install the system images and create the two local emulators, Android 9
   with 2 GB and Android 16 with 6 GB:

   ```bash
   sdkmanager "system-images;android-28;google_apis;x86_64" "system-images;android-36;google_apis;x86_64"
   echo no | avdmanager create avd -n trecos-api28-2g -k "system-images;android-28;google_apis;x86_64" -d pixel_5
   echo no | avdmanager create avd -n trecos-api36-6g -k "system-images;android-36;google_apis;x86_64" -d pixel_5
   ```

   Then set the memory and cores in each AVD's `config.ini`
   (`~/.android/avd/<name>.avd/config.ini`): `hw.ramSize=2048` or `6144`,
   and `hw.cpu.ncore=2`.

3. Boot them without a window, on fixed ports:

   ```bash
   emulator -avd trecos-api28-2g -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect &
   emulator -avd trecos-api36-6g -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect &
   adb -s emulator-5554 wait-for-device
   ```

## Running flows

Install the debug build on an emulator, then run Maestro from the repository
root:

```bash
./gradlew assembleDebug
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
maestro --device emulator-5556 test maestro/flows/app-shell/tabs.yaml      # one flow
maestro --device emulator-5556 test maestro/flows/app-shell                # one area
maestro --device emulator-5556 test --include-tags smoke maestro           # one tier
```

## Writing a flow

- Start with `runFlow: ../../common/start.yaml`. It clears the app and seeds
  the fixture named in `FIXTURE`.
- Find controls by `id:` (the Compose test tag) or by the text a user sees.
  Never use screen coordinates.
- Type only ASCII with `inputText`. Non-ASCII text needs Maestro's own
  keyboard, which can't be kept active on Android 9 once the emulator's
  keyboards are disabled. Names with accents come from the fixtures.
- Full-screen dialogs expose their test tags (`exposeTestTags()`); alert
  dialogs and popup menus don't, so find their buttons by text.
- List the scenarios the flow checks and the screens it visits in its header:

  ```yaml
  # covers: app-shell: Switching tabs
  # screens: home, search, settings
  ```

- Tag it `smoke` when it belongs in the pull request tier, and always `full`.

## Fixtures

Debug and benchmark builds can store fixture data. Release builds ignore it.

| Fixture | Data |
|---|---|
| `one-house` | Apartment: Office with Box A and Box B, four items (laptop, two USB items, one item without a price) |
| `two-houses` | `one-house` plus Parents' house with an attic and three items, one named "Cabeça de impressão" |
| `photos` | `one-house` with three photos on the laptop |
| `1000-items` | One house with 20 boxes and 1,000 items, for the benchmark |

Flows seed through the `fixture` launch argument. From a shell, use the
broadcast instead:

```bash
adb shell am broadcast -n app.trecos/.TestSeedReceiver --es fixture two-houses
```

## Tiers and the matrix

`scripts/device-matrix.py <tier>` lists the emulator cells for each tier:

| Tier | When | Cells | Flows |
|---|---|---|---|
| `pr` | Every pull request | Android 9 / 2 GB and Android 16 / 6 GB | tagged `smoke` |
| `nightly` | Monday to Saturday, 03:00 UTC, on `master` | One per Android version (9 to 16), memory rotating | tagged `full` |
| `weekly` | Sunday, 03:00 UTC | All 36: Android 9 to 16 × 2, 3, 4 and 6 GB | `full` and `roundtrip` |
| `release` | Called by `release.yml` for a `v*` tag | All 36 | `full` and `roundtrip` |

## Running a cell like CI

`scripts/run-device-tests.py` runs a tier on one device and writes
`build/device-results/<cell>/result.json`:

```bash
python3 scripts/run-device-tests.py --device emulator-5554 --tier nightly \
  --cell android9-2g --memory 2048 --apk app/build/outputs/apk/debug/app-x86_64-debug.apk \
  [--instrumented] [--seed 1234]
python3 scripts/summarize-device-results.py build/device-results
```

What it does:
- **Prepares the phone:** disables the keyboards, the voice input and system
  animations, and wakes the screen.
- **Device helper:** starts the helper (below).
- **Order and retries:**
  - runs each flow on its own, in an order shuffled with the printed seed;
  - reruns a failed flow once, and marks a flow that then passes as `flaky`.
- **Evidence:** keeps, for every failed attempt, the screenshots, Maestro's
  step log and `logcat`.
- **Secrets:** replaces the values of the Google test secrets with `***` in
  every output file.

To repeat a CI run exactly, pass the seed printed in the run summary as
`--seed`.

## The device helper

Flows can't run `adb`. `scripts/device-helper.py` runs a fixed list of `adb`
actions over HTTP on `127.0.0.1`, and flows call it through
`maestro/common/device.yaml`:

| Action | Does |
|---|---|
| `screen-lock-on` / `screen-lock-off` | Sets or removes the phone's PIN (`1111`) |
| `recents-hidden` | Whether the app hides its recent-apps preview (`null` on Android 13+, where `dumpsys` doesn't show it) |
| `top-package` | The app in front |
| `mail-apps-off` / `mail-apps-on` | Turns Gmail off or on |
| `push-bad-backups` / `clear-downloads` | Puts a corrupt and a too-new backup in Downloads, or removes Trecos backups |

The runner starts the helper and passes its port as `DEVICE_HELPER_PORT`. When
you run a flow by hand, start it yourself:

```bash
python3 scripts/device-helper.py --device emulator-5556 --port 7556 &
maestro --device emulator-5556 test -e DEVICE_HELPER_PORT=7556 maestro/flows/app-lock/lock.yaml
```

## In CI

`.github/workflows/device-tests.yml`:
- **Matrix:** a first job picks the cells, then each cell boots its emulator
  with `reactivecircus/android-emulator-runner` and runs the runner with
  `--instrumented`.
- **Summary:** a final job merges every cell into the run summary.
- **Artifacts:** each cell's results are kept for 14 days as
  `device-results-<cell>`.

A cell whose emulator never booted is reported in the "Infrastructure" column,
apart from test failures.

## Coverage

`scripts/check-scenario-coverage.py` fails when a `trecos-v1` scenario is
neither named in a flow's `# covers:` header nor listed in
`maestro/COVERAGE.md` with a reason, or when a screen of the app is in no
flow's `# screens:` header.
