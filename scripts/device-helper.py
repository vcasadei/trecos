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
import subprocess
import sys

PIN = "1111"
PACKAGE = "app.trecos"


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
        dict: the PIN, for the flow to type at the unlock prompt.
    """
    adb(device, "shell", "locksettings", "set-pin", PIN)
    return {"pin": PIN}


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


ACTIONS = {
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
