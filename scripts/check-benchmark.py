#!/usr/bin/env python3
"""Performance gate for releases (spec device-testing, "Reference emulator and performance gate").

Reads Macrobenchmark JSON results (StartupBenchmark's coldStartWithBaselineProfile
and ScrollJankTest's scrollingOneThousandItems) and checks them one of two ways:

- ``--baseline FILE``: the release gate on the reference emulator. It fails
  when the median cold start is more than 15% slower than the baseline
  recorded for the same runner and system image. A baseline for another
  runner or image fails too, until it is recorded again on purpose. Jank is
  reported but doesn't gate: the emulator's software GPU makes most frames
  janky.
- ``--budget``: the absolute budget on a real low-end phone (Firebase Test Lab,
  before a build is promoted on Google Play). It fails when the median cold
  start is over 1.5 s, or when 5% or more of the frames while scrolling 1,000
  items are janky. On Android 12+ that is the traced frameOverrunMs (the 95th
  percentile must be on time); on Android 9-11 it is the system's jank share
  from dumpsys gfxinfo (gfxFrameJankPercent).

``--record FILE`` writes a new baseline from results instead of checking.
``--summary FILE`` appends a Markdown table of the numbers (for
$GITHUB_STEP_SUMMARY).

Usage:
    scripts/check-benchmark.py --baseline benchmark/reference-baseline.json \\
        --runner R --image I [--summary FILE] <benchmarkData.json>...
    scripts/check-benchmark.py --budget [--summary FILE] <benchmarkData.json>...
    scripts/check-benchmark.py --record benchmark/reference-baseline.json \\
        --runner R --image I <benchmarkData.json>...
    scripts/check-benchmark.py --self-test
"""
import argparse
import datetime
import json
import os
import sys
import tempfile

MAX_START_MS = 1500.0
MAX_JANK_PERCENT = 5.0
MAX_SLOWDOWN = 0.15


def metric(benchmark, *names):
    """Returns the first metric of the benchmark with one of the names.

    Args:
        benchmark (dict): one entry of a results file's ``benchmarks`` list.
        *names (str): metric names, in order of preference.

    Returns:
        dict | None: the metric, with ``median``, ``P95`` and so on.
    """
    metrics = benchmark.get("metrics", {})
    for name in names:
        if name in metrics:
            return metrics[name]
    return None


def measure(results):
    """Pulls the numbers the gate needs out of Macrobenchmark results.

    Args:
        results (list[dict]): the parsed results files.

    Returns:
        dict: ``start_ms`` (median cold start or None), ``jank_percent`` (the
        gfxinfo median, or None) and ``overrun_p95_ms`` (the traced 95th
        percentile overrun, or None); each None when the result is missing.
    """
    benchmarks = [b for data in results for b in data.get("benchmarks", [])]
    numbers = {"start_ms": None, "jank_percent": None, "overrun_p95_ms": None, "has_scroll": False}
    for b in benchmarks:
        if b.get("name") == "coldStartWithBaselineProfile":
            # Only time to full display: the interactive list, not the first frame. A
            # run without it has no cold start, rather than a smaller number.
            m = metric(b, "timeToFullDisplayMs")
            numbers["start_ms"] = m and m.get("median")
        if b.get("name") == "scrollingOneThousandItems":
            numbers["has_scroll"] = True
            jank = metric(b, "gfxFrameJankPercent")
            numbers["jank_percent"] = jank and jank.get("median")
            overrun = metric(b, "frameOverrunMs")
            numbers["overrun_p95_ms"] = overrun and overrun.get("P95")
    return numbers


def check_budget(numbers):
    """Checks the absolute budget of a real phone.

    Args:
        numbers (dict): from measure().

    Returns:
        list[str]: the failures, empty when the build is within budget.
    """
    failures = []
    if numbers["start_ms"] is None:
        failures.append("no cold start result")
    elif numbers["start_ms"] > MAX_START_MS:
        failures.append(f"median cold start {numbers['start_ms']:.0f} ms is over {MAX_START_MS:.0f} ms")
    if not numbers["has_scroll"]:
        failures.append("no scrolling result")
    elif numbers["jank_percent"] is not None:
        if numbers["jank_percent"] >= MAX_JANK_PERCENT:
            failures.append(f"5% or more of the frames are janky ({numbers['jank_percent']:.1f}% median)")
    elif numbers["overrun_p95_ms"] is None:
        failures.append("scrolling has no frame data")
    elif numbers["overrun_p95_ms"] > 0:
        failures.append(f"5% or more of the frames are janky (95th percentile overrun {numbers['overrun_p95_ms']:.1f} ms)")
    return failures


def check_baseline(numbers, baseline, runner, image):
    """Checks the cold start against the recorded baseline.

    Args:
        numbers (dict): from measure().
        baseline (dict): the baseline file's content.
        runner (str): this run's runner type.
        image (str): this run's emulator system image.

    Returns:
        list[str]: the failures, empty when the release may go out.
    """
    failures = []
    if (baseline.get("runner"), baseline.get("image")) != (runner, image):
        failures.append(f"the baseline is for {baseline.get('runner')} / {baseline.get('image')}, "
                        f"this run used {runner} / {image}: record a new baseline on purpose")
    if numbers["start_ms"] is None:
        failures.append("no cold start result")
    else:
        allowed = baseline["start_median_ms"] * (1 + MAX_SLOWDOWN)
        if numbers["start_ms"] > allowed:
            failures.append(f"median cold start {numbers['start_ms']:.0f} ms is more than 15% slower than "
                            f"the baseline {baseline['start_median_ms']:.0f} ms (allowed {allowed:.0f} ms)")
    return failures


def summary(numbers, baseline=None, runner=None, image=None):
    """Formats the numbers as a Markdown table for the run summary.

    Args:
        numbers (dict): from measure().
        baseline (dict | None): the baseline, in baseline mode.
        runner (str | None): the runner type.
        image (str | None): the system image.

    Returns:
        str: the Markdown text.
    """
    def ms(value):
        return "missing" if value is None else f"{value:.0f} ms"
    lines = ["## Performance", "", "| Measure | Value |", "|---|---|", f"| Median cold start | {ms(numbers['start_ms'])} |"]
    if baseline:
        allowed = baseline["start_median_ms"] * (1 + MAX_SLOWDOWN)
        lines += [f"| Baseline | {baseline['start_median_ms']:.0f} ms (recorded {baseline.get('recorded', '?')}) |",
                  f"| Allowed | {allowed:.0f} ms |"]
    else:
        lines.append(f"| Allowed | {MAX_START_MS:.0f} ms |")
    if numbers["jank_percent"] is not None:
        lines.append(f"| Janky frames (gfxinfo median) | {numbers['jank_percent']:.1f}% |")
    if numbers["overrun_p95_ms"] is not None:
        lines.append(f"| Frame overrun, 95th percentile | {numbers['overrun_p95_ms']:.1f} ms |")
    if runner:
        lines.append(f"| Runner | {runner} |")
    if image:
        lines.append(f"| System image | {image} |")
    return "\n".join(lines) + "\n"


def record(numbers, path, runner, image, today=None):
    """Writes a baseline file from measured numbers.

    Args:
        numbers (dict): from measure().
        path (str): the baseline file.
        runner (str): the runner type.
        image (str): the emulator system image.
        today (str | None): the date to record; today by default.

    Returns:
        dict: what was written.
    """
    if numbers["start_ms"] is None:
        raise ValueError("no cold start result to record")
    baseline = {"runner": runner, "image": image, "start_median_ms": round(numbers["start_ms"]),
                "jank_percent": numbers["jank_percent"], "recorded": today or datetime.date.today().isoformat()}
    # The first baseline is written before its folder is in the repository.
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(baseline, f, indent=2)
        f.write("\n")
    return baseline


def sample(start_ms=1320.0, overrun_p95=-1.2, jank=None):
    """Builds a results file like Macrobenchmark's, for the self-test."""
    scroll = {"gfxFrameJankPercent": {"median": jank}} if jank is not None else {"frameOverrunMs": {"P50": -9.0, "P95": overrun_p95}}
    return [{"benchmarks": [
        {"name": "coldStartWithBaselineProfile", "metrics": {"timeToFullDisplayMs": {"median": start_ms}}},
        {"name": "scrollingOneThousandItems", "metrics": scroll},
    ]}]


def self_test():
    """Checks the gate on sample results, including the spec's scenarios."""
    # Budget on a real phone (scenario "Budgets on a real phone").
    assert check_budget(measure(sample())) == []
    slow = check_budget(measure(sample(start_ms=1700.0)))
    assert any("1700 ms is over 1500 ms" in f for f in slow), slow
    assert any("janky" in f for f in check_budget(measure(sample(overrun_p95=3.5))))
    assert check_budget(measure(sample(jank=2.0))) == []
    assert any("janky" in f for f in check_budget(measure(sample(jank=7.5)))), "the gfxinfo jank share gates too"
    assert check_budget(measure([{"benchmarks": []}])), "missing results block a release"
    first_frame_only = [{"benchmarks": [{"name": "coldStartWithBaselineProfile", "metrics": {"timeToInitialDisplayMs": {"median": 900.0}}}]}]
    assert measure(first_frame_only)["start_ms"] is None, "the first frame alone is not a cold start"
    # Baseline on the reference emulator.
    base = {"runner": "r", "image": "i", "start_median_ms": 1800, "recorded": "2026-10-13"}
    within = check_baseline(measure(sample(start_ms=1900.0, jank=97.7)), base, "r", "i")
    assert within == [], "scenario 'A release within the baseline': jank doesn't gate on the emulator"
    blocked = check_baseline(measure(sample(start_ms=2200.0)), base, "r", "i")
    assert any("2200 ms" in f and "1800 ms" in f and "allowed 2070 ms" in f for f in blocked), blocked
    assert check_baseline(measure(sample(start_ms=1800.0)), base, "other", "i"), "another runner needs a new baseline"
    assert check_baseline(measure([{"benchmarks": []}]), base, "r", "i"), "missing results block a release"
    text = summary(measure(sample(start_ms=1900.0, jank=97.7)), base, "r", "i")
    assert "1900 ms" in text and "1800 ms" in text and "2070 ms" in text and "97.7%" in text and "| Runner | r |" in text
    # Recording a baseline.
    path = os.path.join(tempfile.mkdtemp(), "benchmark", "baseline.json")
    written = record(measure(sample(start_ms=1792.4, jank=97.7)), path, "r", "i", today="2026-10-13")
    with open(path, encoding="utf-8") as f:
        assert json.load(f) == written and written["start_median_ms"] == 1792
    print("check-benchmark self-test OK")


def main(argv):
    """Runs the gate.

    Args:
        argv (list[str]): the command-line arguments, without the program name.

    Returns:
        int: 0 when the gate passes, 1 when it fails, 2 for a usage error.
    """
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--baseline", help="check the cold start against this baseline file")
    mode.add_argument("--budget", action="store_true", help="check the absolute budget of a real phone")
    mode.add_argument("--record", help="write a new baseline file")
    mode.add_argument("--self-test", action="store_true")
    parser.add_argument("--runner", help="the runner type, such as ubuntu-24.04")
    parser.add_argument("--image", help="the emulator system image and revision")
    parser.add_argument("--summary", help="append a Markdown table of the numbers to this file")
    parser.add_argument("results", nargs="*")
    args = parser.parse_args(argv)
    if args.self_test:
        self_test()
        return 0
    if not args.results:
        parser.error("name at least one benchmarkData.json")
    if (args.baseline or args.record) and not (args.runner and args.image):
        parser.error("--baseline and --record need --runner and --image")
    results = []
    for path in args.results:
        with open(path, encoding="utf-8") as f:
            results.append(json.load(f))
    numbers = measure(results)
    if args.record:
        written = record(numbers, args.record, args.runner, args.image)
        print(f"Recorded a {written['start_median_ms']} ms baseline for {args.runner} / {args.image} in {args.record}.")
        return 0
    baseline = None
    if args.baseline:
        with open(args.baseline, encoding="utf-8") as f:
            baseline = json.load(f)
        failures = check_baseline(numbers, baseline, args.runner, args.image)
    else:
        failures = check_budget(numbers)
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as f:
            f.write(summary(numbers, baseline, args.runner, args.image))
            f.write("".join(f"\n- FAIL: {failure}" for failure in failures) + ("\n" if failures else ""))
    for failure in failures:
        print("FAIL:", failure)
    if not failures:
        print("Performance gate passed.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
