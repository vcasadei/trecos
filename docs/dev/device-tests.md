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
