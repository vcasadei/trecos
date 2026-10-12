#!/usr/bin/env python3
"""Checks that every spec scenario and every app screen has device coverage.

A scenario counts as covered when a Maestro flow names it in a header line
``# covers: <capability>: <scenario>``, or when maestro/COVERAGE.md lists it as
"Robolectric only" or "manual" with a reason. A screen (a navigation route of
the app) counts as covered when a flow names it in ``# screens: a, b``.
Anything else fails the check, which names what is missing (spec
device-testing, "Scenario coverage").

Scenarios come from openspec/specs/*/spec.md and from the specs of the changes
in SPEC_CHANGES. Screens are read from the NavHost in TrecosApp.kt: a route
``Routes.ITEM_FORM`` is the screen ``item_form`` and a tab root is its tab's
name (``home``, ``search``, ``settings``).

Usage: scripts/check-scenario-coverage.py [--repo PATH]
Exit status: 0 when everything is covered, 1 otherwise.
"""
import argparse
import pathlib
import re
import sys

SPEC_CHANGES = ("trecos-v1",)
OUTSIDE_FLOWS = ("Robolectric only", "manual")


def spec_scenarios(repo):
    """Lists every scenario in the main specs and in the tracked changes.

    Args:
        repo (pathlib.Path): the repository root.

    Returns:
        set[str]: scenarios as ``<capability>: <scenario name>``.
    """
    roots = [repo / "openspec" / "specs"] + [repo / "openspec" / "changes" / c / "specs" for c in SPEC_CHANGES]
    found = set()
    for root in roots:
        for spec in root.glob("*/spec.md"):
            for line in spec.read_text(encoding="utf-8").splitlines():
                match = re.match(r"^####\s+Scenario:\s*(.+?)\s*$", line)
                if match:
                    found.add(f"{spec.parent.name}: {match.group(1)}")
    return found


def flow_headers(flows_dir):
    """Reads the ``# covers:`` and ``# screens:`` header lines of every flow.

    Args:
        flows_dir (pathlib.Path): the folder holding the flows.

    Returns:
        tuple[set[str], set[str]]: the covered scenarios and the visited screens.
    """
    covers, screens = set(), set()
    for flow in flows_dir.rglob("*.yaml"):
        for line in flow.read_text(encoding="utf-8").splitlines():
            if line.strip() == "---":
                break
            match = re.match(r"^#\s*covers:\s*(.+?)\s*$", line)
            if match:
                covers.add(re.sub(r"\s*:\s*", ": ", match.group(1), count=1))
            match = re.match(r"^#\s*screens:\s*(.+?)\s*$", line)
            if match:
                screens.update(s.strip() for s in match.group(1).split(",") if s.strip())
    return covers, screens


def listed_outside_flows(coverage_md):
    """Reads the scenarios that COVERAGE.md marks as covered outside flows.

    A table row counts when its second column is "Robolectric only" or
    "manual" and its third column (the reason) is not empty.

    Args:
        coverage_md (pathlib.Path): maestro/COVERAGE.md.

    Returns:
        set[str]: the listed scenarios as ``<capability>: <scenario name>``.
    """
    if not coverage_md.exists():
        return set()
    listed = set()
    for line in coverage_md.read_text(encoding="utf-8").splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) >= 3 and cells[1] in OUTSIDE_FLOWS and cells[2]:
            listed.add(re.sub(r"\s*:\s*", ": ", cells[0], count=1))
    return listed


def app_screens(trecos_app):
    """Lists the navigation destinations declared in the app's NavHost.

    Args:
        trecos_app (pathlib.Path): TrecosApp.kt.

    Returns:
        set[str]: screen names, such as ``item_form`` or ``home``.
    """
    text = trecos_app.read_text(encoding="utf-8")
    routes = {m.lower() for m in re.findall(r"composable\(Routes\.([A-Z_]+)", text)}
    tabs = {m.lower() for m in re.findall(r"composable\(rootRoute\(TrecosTab\.(\w+)\)\)", text)}
    return routes | tabs


def check(repo):
    """Compares what exists with what is covered.

    Args:
        repo (pathlib.Path): the repository root.

    Returns:
        tuple[list[str], list[str]]: the uncovered scenarios and the unvisited screens, sorted.
    """
    covers, screens = flow_headers(repo / "maestro" / "flows")
    covered = covers | listed_outside_flows(repo / "maestro" / "COVERAGE.md")
    missing_scenarios = sorted(spec_scenarios(repo) - covered)
    missing_screens = sorted(app_screens(repo / "app" / "src" / "main" / "kotlin" / "app" / "trecos" / "ui" / "shell" / "TrecosApp.kt") - screens)
    return missing_scenarios, missing_screens


def main(argv):
    """Runs the check and prints what is missing.

    Args:
        argv (list[str]): the command-line arguments, without the program name.

    Returns:
        int: 0 when everything is covered, 1 otherwise.
    """
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repo", default=str(pathlib.Path(__file__).resolve().parent.parent))
    args = parser.parse_args(argv)
    missing_scenarios, missing_screens = check(pathlib.Path(args.repo))
    for scenario in missing_scenarios:
        print(f"Scenario without a flow or a reason: {scenario}")
    for screen in missing_screens:
        print(f"Screen no flow visits: {screen}")
    if missing_scenarios or missing_screens:
        print(f"{len(missing_scenarios)} scenarios and {len(missing_screens)} screens are not covered.")
        return 1
    print("Every scenario and screen is covered.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
