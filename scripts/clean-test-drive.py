#!/usr/bin/env python3
"""Empties the Google test account's Drive of Trecos data before Drive flows run.

Deletes the visible "Trecos" folder and, when the token reaches it, every
file in the app's hidden app folder (where the encryption key lives), so a
run never sees the previous run's data (spec device-testing, "Google test account"). It uses only the
Python standard library and reads, from the environment:

- TEST_GOOGLE_OAUTH_CLIENT: JSON with "client_id" and "client_secret" of the
  OAuth client the refresh token was made with (same Cloud project as the app);
- TEST_GOOGLE_DRIVE_TOKEN: that refresh token, for the test account only.

Usage: scripts/clean-test-drive.py [--list]   (--list only prints what it would delete)
Exit status: 0 on success, 1 when Google refuses the token or a call fails.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

TOKEN_URL = "https://oauth2.googleapis.com/token"
FILES_URL = "https://www.googleapis.com/drive/v3/files"
FOLDER = "application/vnd.google-apps.folder"


def access_token(client, refresh_token):
    """Swaps the refresh token for a short-lived access token.

    Args:
        client (dict): the OAuth client, with "client_id" and "client_secret".
        refresh_token (str): the test account's refresh token.

    Returns:
        str: an access token.

    Raises:
        urllib.error.HTTPError: when Google refuses the token.
    """
    body = urllib.parse.urlencode({
        "client_id": client["client_id"],
        "client_secret": client["client_secret"],
        "refresh_token": refresh_token,
        "grant_type": "refresh_token",
    }).encode()
    with urllib.request.urlopen(urllib.request.Request(TOKEN_URL, data=body), timeout=30) as response:
        return json.load(response)["access_token"]


def call(token, url, method="GET"):
    """Calls the Drive API.

    Args:
        token (str): the access token.
        url (str): the full request URL.
        method (str): the HTTP method.

    Returns:
        dict: the decoded JSON answer, empty for a deletion.
    """
    request = urllib.request.Request(url, method=method, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = response.read()
    return json.loads(data) if data else {}


def trecos_files(token):
    """Lists what the cleanup deletes: the Trecos folders and the hidden app files.

    Args:
        token (str): the access token.

    Returns:
        list[tuple[str, str]]: (file id, description) pairs.
    """
    found = []
    query = urllib.parse.urlencode({"q": f"name = 'Trecos' and mimeType = '{FOLDER}' and trashed = false", "fields": "files(id,name)"})
    for f in call(token, f"{FILES_URL}?{query}").get("files", []):
        found.append((f["id"], f"folder {f['name']}"))
    query = urllib.parse.urlencode({"spaces": "appDataFolder", "fields": "files(id,name)", "pageSize": 1000})
    try:
        for f in call(token, f"{FILES_URL}?{query}").get("files", []):
            found.append((f["id"], f"app file {f['name']}"))
    except urllib.error.HTTPError as error:
        # Google's device flow can't grant drive.appdata, so the hidden app folder
        # (the encryption key) may be out of reach; the encryption flows overwrite it.
        if error.code != 403:
            raise
        print("The hidden app folder is out of this token's reach; leaving it.")
    return found


def main(argv):
    """Deletes (or lists) the test account's Trecos data.

    Args:
        argv (list[str]): the command-line arguments, without the program name.

    Returns:
        int: the exit status.
    """
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--list", action="store_true", help="only print what would be deleted")
    args = parser.parse_args(argv)
    try:
        client = json.loads(os.environ["TEST_GOOGLE_OAUTH_CLIENT"])
        token = access_token(client, os.environ["TEST_GOOGLE_DRIVE_TOKEN"])
        files = trecos_files(token)
        for file_id, what in files:
            print(("Would delete " if args.list else "Deleting ") + what)
            if not args.list:
                call(token, f"{FILES_URL}/{file_id}", method="DELETE")
    except KeyError as missing:
        print(f"Missing environment value {missing}.")
        return 1
    except urllib.error.HTTPError as error:
        # The body can echo request details; only the status is printed.
        print(f"Google refused the request: HTTP {error.code}.")
        return 1
    print(f"{len(files)} Trecos entries {'found' if args.list else 'deleted'}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
