#!/usr/bin/env python3
"""Merges the result.json of every device cell into one Markdown summary.

Writes a table with one row per cell (Android version, memory, passed, failed,
flaky, infrastructure) and lists the failed and flaky flows, for the GitHub
run page (spec device-testing, "Device matrix" and "Failure evidence"). Values
of the secret environment variables are replaced with *** in the output.

Usage: scripts/summarize-device-results.py <results-dir> [--out summary.md]
Exit status: 1 when any cell failed or had an infrastructure failure.
"""
import argparse
import json
import os
import pathlib
import sys

SECRET_ENV = ("TEST_GOOGLE_PASSWORD", "TEST_GOOGLE_DRIVE_TOKEN", "TEST_GOOGLE_OAUTH_CLIENT")


def load(results_dir):
    """Reads every result.json below a folder.

    Args:
        results_dir (pathlib.Path): the folder holding one sub-folder per cell.

    Returns:
        list[dict]: the results, sorted by API level and memory.
    """
    results = [json.loads(p.read_text(encoding="utf-8")) for p in results_dir.rglob("result.json")]
    return sorted(results, key=lambda r: (r.get("api", 0), r.get("memory", 0), r.get("cell", "")))


def count(result, status):
    """Counts the flows in a result with one status.

    Args:
        result (dict): one cell's result.
        status (str): "passed", "failed" or "flaky".

    Returns:
        int: how many flows have that status.
    """
    return sum(1 for f in result.get("flows", []) if f["status"] == status)


def summarize(results, secrets=()):
    """Builds the Markdown summary.

    Args:
        results (list[dict]): the cells' results.
        secrets (Iterable[str]): values to replace with *** in the output.

    Returns:
        tuple[str, bool]: the Markdown text, and whether every cell passed.
    """
    lines = ["## Device tests", "", "| Cell | Android | Memory | Passed | Failed | Flaky | Infrastructure |", "|---|---|---|---|---|---|---|"]
    problems, ok = [], True
    for r in results:
        failed, flaky = count(r, "failed"), count(r, "flaky")
        infra = "failure" if r.get("infrastructure") else ""
        if r.get("instrumented") == "failed":
            problems.append(f"- {r.get('cell')}: instrumented tests failed")
            ok = False
        if failed or infra:
            ok = False
        memory = f"{r.get('memory', 0) // 1024} GB" if r.get("memory") else ""
        lines.append(f"| {r.get('cell') or r.get('device')} | {r.get('android', '')} | {memory} | {count(r, 'passed')} | {failed} | {flaky} | {infra} |")
        for f in r.get("flows", []):
            if f["status"] in ("failed", "flaky"):
                problems.append(f"- {r.get('cell') or r.get('device')}: `{f['flow']}` {f['status']}")
    if problems:
        lines += ["", "### Failed and flaky", ""] + problems
    seeds = sorted({str(r.get("seed")) for r in results if r.get("seed") is not None})
    if seeds:
        lines += ["", f"Shuffle seeds: {', '.join(seeds)}"]
    text = "\n".join(lines) + "\n"
    for secret in secrets:
        if secret:
            text = text.replace(secret, "***")
    return text, ok


def main(argv):
    """Prints or writes the summary of a results folder.

    Args:
        argv (list[str]): the command-line arguments, without the program name.

    Returns:
        int: 0 when every cell passed, 1 otherwise.
    """
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("results")
    parser.add_argument("--out")
    args = parser.parse_args(argv)
    text, ok = summarize(load(pathlib.Path(args.results)), [os.environ.get(k, "") for k in SECRET_ENV])
    if args.out:
        pathlib.Path(args.out).write_text(text, encoding="utf-8")
    print(text)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
