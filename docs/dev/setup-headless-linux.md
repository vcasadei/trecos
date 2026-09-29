# Headless Linux setup

How to build, test and run Trecos on an x86_64 Ubuntu machine with no screen.
Screenshot tests (Robolectric + Roborazzi) need only the JDK and the SDK; the
emulator is needed only for Macrobenchmark and Baseline Profile generation.

Tested on Ubuntu 24.04 LTS, x86_64.

## What gets installed

| Component | Version | Location | Needs sudo |
|---|---|---|---|
| OpenJDK | 17 (headless) | `/usr/lib/jvm/java-17-openjdk-amd64` | yes |
| Android command-line tools | `commandlinetools-linux-15859902` | `~/Android/Sdk/cmdline-tools/latest` | no |
| Platform | `platforms;android-36` (Play target SDK) | `~/Android/Sdk/platforms` | no |
| Build tools | `build-tools;36.1.0` | `~/Android/Sdk/build-tools` | no |
| Platform tools (`adb`) | latest | `~/Android/Sdk/platform-tools` | no |
| Emulator + system image | `emulator`, `system-images;android-36;google_apis;x86_64` | `~/Android/Sdk` | no |
| Emulator libraries | see step 5 | system | yes |

## 1. JDK 17

```sh
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk-headless unzip
java -version   # openjdk version "17..."
```

## 2. Android command-line tools

Check the current file name and SHA-256 on
<https://developer.android.com/studio#command-line-tools-only> and update
them here when they change.

```sh
cd /tmp
curl -sSLO https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
echo "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  commandlinetools-linux-15859902_latest.zip" | sha256sum -c
mkdir -p ~/Android/Sdk/cmdline-tools
unzip -q commandlinetools-linux-15859902_latest.zip -d ~/Android/Sdk/cmdline-tools
mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest
```

`sdkmanager` expects the tools under `cmdline-tools/latest`; the `mv` is not optional.

## 3. Environment variables

Append to `~/.bashrc`, then open a new shell:

```sh
# Android SDK (Trecos: docs/dev/setup-headless-linux.md)
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

## 4. SDK packages

```sh
yes | sdkmanager --licenses
sdkmanager "platforms;android-36" "build-tools;36.1.0" "platform-tools"
sdkmanager --list_installed
```

`sdkmanager` prints a deprecation notice pointing to the new `android sdk`
command; both work, and the commands here use `sdkmanager`.

## 5. Emulator (benchmarks and Baseline Profiles only)

### KVM access

```sh
grep -cE 'vmx|svm' /proc/cpuinfo   # > 0: CPU virtualization available
sudo usermod -aG kvm "$USER"        # then log out and back in
id | grep -o kvm                    # must print "kvm"
```

### Libraries

The emulator loads X11, GL and audio libraries even with `-no-window`. Without
them it exits with `Could not open libX11-xcb.so.1, give up`.

```sh
sudo apt-get install -y libx11-xcb1 libice6 libsm6 libxi6 libpulse0 libxkbfile1 \
  libnss3 libxcomposite1 libxcursor1 libxdamage1 libxtst6 libgl1
```

### Image and virtual device

The `google_apis` image allows `adb root`, which Baseline Profile generation needs.

```sh
sdkmanager "emulator" "system-images;android-36;google_apis;x86_64"
echo no | avdmanager create avd -n trecos-api36 \
  -k "system-images;android-36;google_apis;x86_64" -d pixel_6
```

An `Error: .../devices.xml` line from `avdmanager` is harmless if
`~/.android/avd/trecos-api36.avd` exists afterwards.

### Boot without a window

```sh
nohup emulator -avd trecos-api36 -no-window -no-audio -no-boot-anim \
  -gpu swiftshader_indirect -no-snapshot > /tmp/emulator.log 2>&1 &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
adb devices    # emulator-5554  device
```

The first boot takes one to two minutes. Stop it with `adb emu kill`.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `emulator: command not found` | `$ANDROID_HOME/emulator` not on `PATH` | Step 3, then a new shell |
| `Could not open libX11-xcb.so.1` | Missing emulator libraries | Step 5, Libraries |
| `/dev/kvm` permission denied | Not in the `kvm` group, or no re-login yet | Step 5, KVM access |
| `sdkmanager` can't find its own path | Tools not under `cmdline-tools/latest` | Redo the `mv` in step 2 |
