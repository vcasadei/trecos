#!/usr/bin/env python3
"""Runs whitelisted adb commands for Maestro flows, over HTTP on localhost.

Maestro flows can't run adb, but their ``runScript`` steps can make HTTP
calls from the computer running Maestro. This helper answers
``POST /<action>`` for the actions in ACTIONS only, against one device, so a
flow can set the phone's PIN, remove it, or read what only adb can see.
Anything else gets 404. It listens on 127.0.0.1 only.

Usage: scripts/device-helper.py --device emulator-5554 --port 7001
Flows reach it through maestro/common/device.yaml, with the port in the
DEVICE_HELPER_PORT environment value (run-device-tests.py starts the helper
and passes the port).
"""
import argparse
import http.server
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import time
import zipfile

PIN = "1111"
PACKAGE = "app.trecos"
MAIL_APPS = ("com.google.android.gm",)


def adb(device, *args):
    """Runs one adb command against the device.

    Args:
        device (str): the device serial.
        *args (str): the adb arguments.

    Returns:
        str: the command's standard output.
    """
    return subprocess.run(["adb", "-s", device, *args], capture_output=True, text=True, timeout=60).stdout


def secure_window(device):
    """Tells whether the app's window hides its content from screenshots and recent apps.

    Args:
        device (str): the device serial.

    Returns:
        bool: True when a Trecos window has FLAG_SECURE.
    """
    windows = adb(device, "shell", "dumpsys", "window", "windows")
    blocks = [b for b in windows.split("Window #") if PACKAGE in b]
    return any("SECURE" in b for b in blocks)


def screen_lock_on(device):
    """Sets the phone's PIN to PIN, so it has a screen lock.

    Args:
        device (str): the device serial.

    Returns:
        dict: the PIN, for the flow to type at the unlock prompt, and whether
        Android confirmed it.
    """
    # A disabled lock screen (seen on Android 9 emulators) makes the phone look
    # unsecured even with a PIN, so it is turned back on first. The PIN is then
    # verified, and set again if Android didn't take it.
    for _ in range(3):
        adb(device, "shell", "locksettings", "set-disabled", "false")
        adb(device, "shell", "locksettings", "set-pin", PIN)
        if "verified successfully" in adb(device, "shell", "locksettings", "verify", "--old", PIN):
            return {"pin": PIN, "verified": True}
        time.sleep(2)
    return {"pin": PIN, "verified": False}


def screen_lock_off(device):
    """Removes the phone's PIN, leaving it with no screen lock.

    Args:
        device (str): the device serial.

    Returns:
        dict: an empty result.
    """
    adb(device, "shell", "locksettings", "clear", "--old", PIN)
    return {}


def recents_hidden(device):
    """Tells whether the app keeps its content out of the recent-apps preview.

    Android 9 to 12 do it with FLAG_SECURE, which dumpsys shows. Android 13
    and later use setRecentsScreenshotEnabled, which dumpsys doesn't report,
    so there the answer is None (unknown) and the unit test covers it.

    Args:
        device (str): the device serial.

    Returns:
        dict: ``hidden`` as True, False or None.
    """
    if int(adb(device, "shell", "getprop", "ro.build.version.sdk").strip() or 0) >= 33:
        return {"hidden": None}
    return {"hidden": secure_window(device)}


def top_package(device):
    """Names the app whose screen is in front.

    Args:
        device (str): the device serial.

    Returns:
        dict: ``package``, such as ``com.google.android.gm``.
    """
    for line in adb(device, "shell", "dumpsys", "activity", "activities").splitlines():
        if "ResumedActivity" in line and "/" in line:
            return {"package": line.split("/")[0].split()[-1]}
    return {"package": ""}


def mail_apps(device, enabled):
    """Turns the phone's e-mail apps (Gmail) on or off.

    Args:
        device (str): the device serial.
        enabled (bool): whether they should be on.

    Returns:
        dict: an empty result.
    """
    for package in MAIL_APPS:
        adb(device, "shell", "pm", "enable" if enabled else "disable-user", package)
    return {}


def push_bad_backups(device):
    """Puts two unusable backups in the phone's Downloads folder.

    ``trecos-corrupt.zip`` is not a zip at all; ``trecos-newer.zip`` is a valid
    zip whose manifest has a format version far newer than any app knows.

    Args:
        device (str): the device serial.

    Returns:
        dict: the file names.
    """
    with tempfile.TemporaryDirectory() as folder:
        corrupt = os.path.join(folder, "trecos-corrupt.zip")
        with open(corrupt, "wb") as out:
            out.write(b"this is not a zip file")
        newer = os.path.join(folder, "trecos-newer.zip")
        with zipfile.ZipFile(newer, "w") as archive:
            archive.writestr("manifest.json", json.dumps(
                {"formatVersion": 999, "createdAt": 0, "appVersion": "99.0.0", "houses": []}))
        for path in (corrupt, newer):
            adb(device, "push", path, "/sdcard/Download/")
            adb(device, "shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                "-d", "file:///sdcard/Download/" + os.path.basename(path))
    return {"files": ["trecos-corrupt.zip", "trecos-newer.zip"]}


def clear_downloads(device):
    """Deletes Trecos backups from the phone's Downloads folder.

    Args:
        device (str): the device serial.

    Returns:
        dict: an empty result.
    """
    adb(device, "shell", "rm", "-f", "/sdcard/Download/trecos-*.zip")
    return {}


def add_photos(device):
    """Copies the test photos in maestro/media to the phone's Pictures and Download folders.

    Maestro's own addMedia fails on a freshly booted Android 9 emulator, so the
    photo flows use this instead.

    Args:
        device (str): the device serial.

    Returns:
        dict: how many photos were added.
    """
    media = pathlib.Path(__file__).resolve().parent.parent / "maestro" / "media"
    photos = sorted(media.glob("*.png"))
    # Pictures feeds the photo picker; Downloads feeds Android 9's Files app,
    # which offers only Recent and Downloads on these emulator images.
    for folder in ("/sdcard/Pictures", "/sdcard/Download"):
        adb(device, "shell", "mkdir", "-p", folder)
        for photo in photos:
            adb(device, "push", str(photo), folder + "/")
            adb(device, "shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                "-d", "file://" + folder + "/" + photo.name)
    time.sleep(2)
    return {"added": len(photos)}


def wipe_drive(device):
    """Empties the signed-in test account's Drive of Trecos data, through the app.

    The debug app's TestSeedReceiver deletes the Trecos folder and the hidden
    app folder of TEST_GOOGLE_ACCOUNT with the app's own Google authorization, which reaches the
    hidden folder that scripts/clean-test-drive.py can't.

    Args:
        device (str): the device serial.

    Returns:
        dict: the app's answer, such as "wiped 3" or "no-account".
    """
    account = os.environ.get("TEST_GOOGLE_ACCOUNT", "")
    if not account:
        return {"result": "no-account"}
    done = adb(device, "shell", "am", "broadcast", "-n", f"{PACKAGE}/.TestSeedReceiver", "--es", "action", "wipe-drive",
               "--es", "account", account)
    answer = done.split('data="', 1)[1].split('"', 1)[0] if 'data="' in done else "no answer"
    return {"result": answer}


# The app's folders a snapshot keeps: its database, files and settings.
APP_DIRS = ("databases", "files", "no_backup", "shared_prefs")
SNAPSHOTS = pathlib.Path(tempfile.gettempdir()) / "trecos-device-helper"


def app_snapshot(device):
    """Saves the app's data on the computer, as "the phone" of a two-device flow.

    Between app_snapshot and app_restore a flow can clear the app and use it as
    a second device on the same Google account; restoring brings the first
    device back, with its device id and unsynced changes. Needs a debuggable
    build (run-as).

    Args:
        device (str): the device serial.

    Returns:
        dict: the snapshot's size in bytes.
    """
    adb(device, "shell", "am", "force-stop", PACKAGE)
    present = adb(device, "shell", "run-as", PACKAGE, "ls").split()
    dirs = [d for d in APP_DIRS if d in present]
    data = subprocess.run(["adb", "-s", device, "exec-out", "run-as", PACKAGE, "tar", "-cf", "-", *dirs],
                          capture_output=True, timeout=120).stdout
    SNAPSHOTS.mkdir(exist_ok=True)
    (SNAPSHOTS / f"{device}.tar").write_bytes(data)
    return {"bytes": len(data)}


def app_restore(device):
    """Puts back the app data app_snapshot saved, replacing the current data.

    Args:
        device (str): the device serial.

    Returns:
        dict: whether a snapshot was restored.
    """
    snapshot = SNAPSHOTS / f"{device}.tar"
    if not snapshot.exists():
        return {"restored": False}
    adb(device, "shell", "am", "force-stop", PACKAGE)
    adb(device, "shell", "run-as", PACKAGE, "rm", "-rf", *APP_DIRS)
    subprocess.run(["adb", "-s", device, "exec-in", "run-as", PACKAGE, "tar", "-xf", "-"],
                   input=snapshot.read_bytes(), capture_output=True, timeout=120)
    snapshot.unlink()
    return {"restored": True}


ACTIONS = {
    "wipe-drive": wipe_drive,
    "app-snapshot": app_snapshot,
    "app-restore": app_restore,
    "add-photos": add_photos,
    "push-bad-backups": push_bad_backups,
    "clear-downloads": clear_downloads,
    "top-package": top_package,
    "mail-apps-off": lambda device: mail_apps(device, False),
    "mail-apps-on": lambda device: mail_apps(device, True),
    "screen-lock-on": screen_lock_on,
    "screen-lock-off": screen_lock_off,
    "recents-hidden": recents_hidden,
}


def handler_for(device):
    """Builds the request handler bound to one device.

    Args:
        device (str): the device serial.

    Returns:
        type: an http.server.BaseHTTPRequestHandler subclass.
    """
    class Handler(http.server.BaseHTTPRequestHandler):
        """Answers POST /<action> with the action's JSON result."""

        def do_POST(self):  # noqa: N802 - the name http.server expects
            """Runs the action named by the path, if it is whitelisted."""
            action = self.path.strip("/")
            if action not in ACTIONS:
                self.send_error(404, "unknown action")
                return
            body = json.dumps(ACTIONS[action](device)).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format, *args):  # noqa: A002 - signature from http.server
            """Keeps the runner's output quiet."""

    return Handler


def main(argv):
    """Serves the helper until interrupted.

    Args:
        argv (list[str]): the command-line arguments, without the program name.

    Returns:
        int: the exit status.
    """
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--device", required=True)
    parser.add_argument("--port", type=int, required=True)
    args = parser.parse_args(argv)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", args.port), handler_for(args.device))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
