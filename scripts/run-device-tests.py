#!/usr/bin/env python3
"""Runs the device suite on one emulator cell and writes its result.json.

Each flow runs on its own, in an order shuffled with a printed seed, so a
run can be repeated exactly. A failed flow runs once more: passing then marks
it "flaky" (spec device-testing, "Isolated and repeatable flows"). For every
failed attempt the flow's screenshots, Maestro's step log and the device log
are kept (spec "Failure evidence"). Values of the secret environment
variables in SECRET_ENV are replaced with *** in every output file.

Usage (from the repository root):
    scripts/run-device-tests.py --device emulator-5554 --tier pr \\
        --apk app/build/outputs/apk/debug/app-x86_64-debug.apk

Exit status: 0 when every flow passed (flaky included), 1 when a flow or the
instrumented tests failed, 2 for an infrastructure failure (no device).
"""
import argparse
import json
import os
import pathlib
import platform
import random
import re
import shutil
import socket
import subprocess
import sys
import time

REPO = pathlib.Path(__file__).resolve().parent.parent
FLOWS = REPO / "maestro" / "flows"
PACKAGE = "app.trecos"
FLOW_TIMEOUT_S = 600
# Apps that provide input methods on the emulator images: Gboard, the AOSP keyboard, and
# the Google app, whose voice input Android 9 switches to when no keyboard is left.
KEYBOARDS = ("com.google.android.inputmethod.latin", "com.android.inputmethod.latin", "com.google.android.googlequicksearchbox")
MAESTRO_IME_PACKAGE = "dev.mobile.maestro/"
SECRET_ENV = ("TEST_GOOGLE_PASSWORD", "TEST_GOOGLE_DRIVE_TOKEN", "TEST_GOOGLE_OAUTH_CLIENT")
# The environment variables passed into flows; secret ones are redacted from outputs.
FLOW_ENV = ("TEST_GOOGLE_ACCOUNT",) + SECRET_ENV
# The PIN scripts/device-helper.py sets; the runner clears it around every flow.
HELPER_PIN = "1111"
TIER_TAGS = {
    "pr": {"smoke"},
    "nightly": {"full"},
    "weekly": {"full", "roundtrip"},
    "release": {"full", "roundtrip"},
    "drive": {"drive"},
    "roundtrip": {"roundtrip"},
}


def flow_tags(path):
    """Reads the tags in a flow's header.

    Args:
        path (pathlib.Path): the flow file.

    Returns:
        set[str]: the flow's tags; empty when it has none.
    """
    tags, in_tags = set(), False
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() == "---":
            break
        if re.match(r"^tags:\s*$", line):
            in_tags = True
            continue
        item = re.match(r"^\s+-\s*(\S+)\s*$", line)
        if in_tags and item:
            tags.add(item.group(1))
        elif in_tags and line and not line.startswith(" "):
            in_tags = False
    return tags


def select_flows(flows_dir, tags):
    """Lists the flows carrying at least one of the tags, sorted by path.

    Args:
        flows_dir (pathlib.Path): the folder holding the flows.
        tags (set[str]): the tags to select.

    Returns:
        list[pathlib.Path]: the selected flow files.
    """
    return sorted(p for p in flows_dir.rglob("*.yaml") if flow_tags(p) & tags)


def adb(device, *args, timeout=60):
    """Runs an adb command against one device.

    Args:
        device (str): the device serial, such as ``emulator-5554``.
        *args (str): the adb arguments.
        timeout (int): seconds before giving up.

    Returns:
        subprocess.CompletedProcess: the finished command, output as text.
    """
    return subprocess.run(["adb", "-s", device, *args], capture_output=True, text=True, timeout=timeout)


def device_ready(device):
    """Tells whether the device is connected and has finished booting.

    Args:
        device (str): the device serial.

    Returns:
        bool: True when ``sys.boot_completed`` is 1.
    """
    try:
        return adb(device, "shell", "getprop", "sys.boot_completed").stdout.strip() == "1"
    except (subprocess.SubprocessError, OSError):
        return False


def prop(device, name):
    """Reads one system property.

    Args:
        device (str): the device serial.
        name (str): the property name.

    Returns:
        str: the value, empty when unknown.
    """
    return adb(device, "shell", "getprop", name).stdout.strip()


def free_port():
    """Finds a free TCP port on localhost for the device helper.

    Returns:
        int: the port number.
    """
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def prepare(device):
    """Makes the emulator predictable for flows.

    Disables the on-screen keyboards and other input methods, which
    autocorrect or cover what flows type (Gboard turned "dos meus" into "do
    mess" on Android 9), turns off system animations, and wakes the screen.

    Args:
        device (str): the device serial.
    """
    for package in KEYBOARDS:
        adb(device, "shell", "pm", "disable-user", "--user", "0", package)
    # Whatever input method is left (Android 9 falls back to Google's voice input, whose
    # panel covers the screen) is switched off too, except Maestro's own, which types
    # non-ASCII text such as "Cabeça" and neither autocorrects nor covers the screen.
    for ime in adb(device, "shell", "ime", "list", "-s").stdout.split():
        if not ime.startswith(MAESTRO_IME_PACKAGE):
            adb(device, "shell", "ime", "disable", ime)
    for scale in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb(device, "shell", "settings", "put", "global", scale, "0")
    adb(device, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb(device, "shell", "wm", "dismiss-keyguard")


def reset_phone(device):
    """Undoes what the device helper can change: the PIN and disabled e-mail apps.

    Args:
        device (str): the device serial.
    """
    adb(device, "shell", "locksettings", "clear", "--old", HELPER_PIN)
    adb(device, "shell", "pm", "enable", "com.google.android.gm")


def run_flow(device, flow, out_dir, env):
    """Runs one flow once with Maestro, keeping its outputs.

    Args:
        device (str): the device serial.
        flow (pathlib.Path): the flow file.
        out_dir (pathlib.Path): where this attempt's files go.
        env (dict[str, str]): values passed to the flow with ``-e``.

    Returns:
        bool: True when the flow passed.
    """
    out_dir.mkdir(parents=True, exist_ok=True)
    adb(device, "logcat", "-c")
    # A flow that changed the phone and then failed must not leave it changed for the next flow.
    reset_phone(device)
    command = ["maestro", "--device", device, "test", str(flow), "--format", "junit",
               "--output", str(out_dir / "junit.xml"), "--test-output-dir", str(out_dir)]
    for key, value in env.items():
        command += ["-e", f"{key}={value}"]
    try:
        done = subprocess.run(command, capture_output=True, text=True, timeout=FLOW_TIMEOUT_S, cwd=REPO)
        passed, log = done.returncode == 0, done.stdout + done.stderr
    except subprocess.TimeoutExpired as timeout:
        passed, log = False, f"Timed out after {FLOW_TIMEOUT_S} s\n{timeout.stdout or ''}"
    (out_dir / "maestro.log").write_text(log, encoding="utf-8")
    reset_phone(device)
    if not passed:
        (out_dir / "logcat.txt").write_text(adb(device, "logcat", "-d", timeout=120).stdout, encoding="utf-8")
    else:
        shutil.rmtree(out_dir, ignore_errors=True)
    return passed


def redact(out_dir, secrets):
    """Replaces every secret value in the text files under a folder with ***.

    Args:
        out_dir (pathlib.Path): the folder to clean.
        secrets (list[str]): the values to remove; empty ones are ignored.
    """
    secrets = [s for s in secrets if s]
    if not secrets or not out_dir.exists():
        return
    for path in out_dir.rglob("*"):
        if not path.is_file():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        cleaned = text
        for secret in secrets:
            cleaned = cleaned.replace(secret, "***")
        if cleaned != text:
            path.write_text(cleaned, encoding="utf-8")


def instrumented(device):
    """Runs the app's instrumented tests (app/src/androidTest) on the device.

    Args:
        device (str): the device serial.

    Returns:
        str: "passed" or "failed".
    """
    done = subprocess.run(["./gradlew", "--no-daemon", "-q", "connectedDebugAndroidTest"], cwd=REPO,
                          env={**os.environ, "ANDROID_SERIAL": device})
    return "passed" if done.returncode == 0 else "failed"


def main(argv):
    """Runs a tier on one device and writes result.json.

    Args:
        argv (list[str]): the command-line arguments, without the program name.

    Returns:
        int: 0 when everything passed, 1 on a test failure, 2 when the device is missing.
    """
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--device", required=True)
    parser.add_argument("--tier", required=True, choices=sorted(TIER_TAGS))
    parser.add_argument("--apk", help="install this APK first")
    parser.add_argument("--cell", default="", help="the cell's name in the matrix, such as android9-2g")
    parser.add_argument("--memory", type=int, default=0, help="the cell's memory in MB")
    parser.add_argument("--seed", type=int, default=random.SystemRandom().randrange(1_000_000))
    parser.add_argument("--out", default=str(REPO / "build" / "device-results"))
    parser.add_argument("--flows", default=str(FLOWS))
    parser.add_argument("--instrumented", action="store_true", help="also run connectedDebugAndroidTest (then --apk is needed)")
    args = parser.parse_args(argv)
    if args.instrumented and not args.apk:
        parser.error("--instrumented uninstalls the app, so --apk is needed to reinstall it")

    out = pathlib.Path(args.out) / (args.cell or args.device)
    out.mkdir(parents=True, exist_ok=True)
    result = {"cell": args.cell, "device": args.device, "tier": args.tier, "memory": args.memory, "seed": args.seed,
              "runner": os.environ.get("RUNNER_NAME", platform.node()), "infrastructure": False, "flows": []}
    if not device_ready(args.device):
        result["infrastructure"] = True
        (out / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(f"{args.device} is not booted: infrastructure failure")
        return 2
    result.update(api=int(prop(args.device, "ro.build.version.sdk") or 0), android=prop(args.device, "ro.build.version.release"),
                  image=prop(args.device, "ro.build.fingerprint"))
    prepare(args.device)
    # The instrumented tests uninstall the app when they finish, so they run before the install.
    if args.instrumented:
        result["instrumented"] = instrumented(args.device)
    if args.apk:
        adb(args.device, "install", "-r", args.apk, timeout=300).check_returncode()

    flows = select_flows(pathlib.Path(args.flows), TIER_TAGS[args.tier])
    random.Random(args.seed).shuffle(flows)
    print(f"Running {len(flows)} flows on {args.device} with shuffle seed {args.seed}")
    env = {k: os.environ[k] for k in FLOW_ENV if os.environ.get(k)}
    helper_port = free_port()
    helper = subprocess.Popen([sys.executable, str(REPO / "scripts" / "device-helper.py"), "--device", args.device, "--port", str(helper_port)])
    env["DEVICE_HELPER_PORT"] = str(helper_port)
    for flow in flows:
        name = str(flow.relative_to(pathlib.Path(args.flows))).removesuffix(".yaml")
        start = time.monotonic()
        attempts = 1
        passed = run_flow(args.device, flow, out / name / "attempt-1", env)
        if not passed:
            attempts = 2
            retried = run_flow(args.device, flow, out / name / "attempt-2", env)
            status = "flaky" if retried else "failed"
        else:
            status = "passed"
        result["flows"].append({"flow": name, "status": status, "attempts": attempts, "seconds": round(time.monotonic() - start, 1)})
        print(f"  {status:7} {name}")

    helper.terminate()
    (out / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    redact(out, [os.environ.get(k, "") for k in SECRET_ENV])
    failed = [f for f in result["flows"] if f["status"] == "failed"] or result.get("instrumented") == "failed"
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
