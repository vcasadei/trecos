#!/usr/bin/env python3
"""Prints the GitHub Actions matrix of emulator cells for a device-test tier.

The matrix covers Android 9 to 16 (API 28 to 36) and 2, 3, 4 and 6 GB of
memory (spec device-testing, "Device matrix" and "Run tiers"):

- ``pr``: the two corner cells, Android 9 / 2 GB and Android 16 / 6 GB.
- ``nightly``: one cell per Android version; the memory size rotates with the
  day of the year, so each version runs every size within four nights.
- ``weekly`` and ``release``: all 36 cells.

With ``--drive`` it prints the cells of the Drive job instead, which signs in
with the Google test account (spec "Google test account"): none for ``pr``,
Android 16 / 4 GB nightly, and Android 16 / 2 GB as well weekly and for a
release. Only Android 16: the older emulator images ship a 2020 Google Play
services that can't update and predates the Authorization API Trecos signs in
with.

Usage: ``scripts/device-matrix.py <tier> [--day N] [--drive]``. The output is a
JSON object with an ``include`` list, ready for ``strategy.matrix``.
"""
import argparse
import datetime
import json
import sys

APIS = list(range(28, 37))
MEMORY_MB = [2048, 3072, 4096, 6144]
ANDROID = {28: "9", 29: "10", 30: "11", 31: "12", 32: "12L", 33: "13", 34: "14", 35: "15", 36: "16"}
TIERS = ("pr", "nightly", "weekly", "release")


def cell(api, memory_mb):
    """Describes one emulator cell.

    Args:
        api (int): the Android API level.
        memory_mb (int): the emulator's memory in megabytes.

    Returns:
        dict: the cell's API level, Android version, memory and a short name.
    """
    return {
        "api": api,
        "android": ANDROID[api],
        "memory": memory_mb,
        "name": f"android{ANDROID[api]}-{memory_mb // 1024}g",
    }


def matrix(tier, day):
    """Lists the cells a tier runs on.

    Args:
        tier (str): one of ``pr``, ``nightly``, ``weekly`` or ``release``.
        day (int): the day of the year, which picks the nightly memory sizes.

    Returns:
        list[dict]: the cells, in API order.

    Raises:
        ValueError: when the tier is unknown.
    """
    if tier == "pr":
        return [cell(APIS[0], MEMORY_MB[0]), cell(APIS[-1], MEMORY_MB[-1])]
    if tier == "nightly":
        return [cell(api, MEMORY_MB[(day + api) % len(MEMORY_MB)]) for api in APIS]
    if tier in ("weekly", "release"):
        return [cell(api, memory) for api in APIS for memory in MEMORY_MB]
    raise ValueError(f"unknown tier: {tier}")


def drive_matrix(tier):
    """Lists the cells the Drive job runs on for a tier.

    Args:
        tier (str): one of ``pr``, ``nightly``, ``weekly`` or ``release``.

    Returns:
        list[dict]: the cells; empty for pull requests, which never get the secrets.

    Raises:
        ValueError: when the tier is unknown.
    """
    if tier not in TIERS:
        raise ValueError(f"unknown tier: {tier}")
    if tier == "pr":
        return []
    cells = [cell(36, 4096)]
    if tier in ("weekly", "release"):
        cells.insert(0, cell(36, 2048))
    return [dict(c, name="drive-" + c["name"]) for c in cells]


def main(argv):
    """Prints the matrix for the tier named on the command line.

    Args:
        argv (list[str]): the command-line arguments, without the program name.

    Returns:
        int: the exit status.
    """
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("tier", choices=TIERS)
    parser.add_argument("--day", type=int, default=datetime.date.today().timetuple().tm_yday)
    parser.add_argument("--drive", action="store_true", help="the Drive job's cells")
    args = parser.parse_args(argv)
    cells = drive_matrix(args.tier) if args.drive else matrix(args.tier, args.day)
    print(json.dumps({"include": cells}))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
