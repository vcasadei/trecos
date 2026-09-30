#!/usr/bin/env python3
"""Release gate for the performance pass (spec "Performance on low-end phones", task 14.8).

Reads the Macrobenchmark JSON results and fails when, on the reference phone,
the median cold start to the interactive Home screen (with the Baseline
Profile) is over 1.5 s, or 5% or more of the frames while scrolling 1,000
items are janky (frames that missed their deadline: frameOverrunMs > 0, so the
95th percentile must be on time).

Usage: scripts/check-benchmark.py <benchmarkData.json>...
       scripts/check-benchmark.py --self-test
"""
import json
import sys

MAX_START_MS = 1500.0


def metric(benchmark, *names):
    """Returns the first metric of the benchmark with one of the names."""
    metrics = benchmark.get("metrics", {})
    for name in names:
        if name in metrics:
            return metrics[name]
    return None


def check(results):
    """Returns a list of failures, empty when the release may go out."""
    failures = []
    benchmarks = [b for data in results for b in data.get("benchmarks", [])]
    start = [b for b in benchmarks if b.get("name") == "coldStartWithBaselineProfile"]
    scroll = [b for b in benchmarks if b.get("name") == "scrollingOneThousandItems"]
    if not start:
        failures.append("no coldStartWithBaselineProfile result")
    for b in start:
        m = metric(b, "timeToFullDisplayMs", "timeToInitialDisplayMs")
        median = m and m.get("median")
        if median is None:
            failures.append("cold start has no timing")
        elif median > MAX_START_MS:
            failures.append(f"median cold start {median:.0f} ms is over {MAX_START_MS:.0f} ms")
    if not scroll:
        failures.append("no scrollingOneThousandItems result")
    for b in scroll:
        m = metric(b, "frameOverrunMs")
        p95 = m and m.get("P95")
        if p95 is None:
            failures.append("scrolling has no frame overrun data")
        elif p95 > 0:
            failures.append(f"5% or more of the frames are janky (95th percentile overrun {p95:.1f} ms)")
    return failures


def self_test():
    ok = [{"benchmarks": [
        {"name": "coldStartWithBaselineProfile", "metrics": {"timeToFullDisplayMs": {"median": 1320.0}}},
        {"name": "scrollingOneThousandItems", "metrics": {"frameOverrunMs": {"P50": -9.0, "P95": -1.2}}},
    ]}]
    assert check(ok) == [], check(ok)
    slow = json.loads(json.dumps(ok))
    slow[0]["benchmarks"][0]["metrics"]["timeToFullDisplayMs"]["median"] = 1610.0
    assert any("cold start" in f for f in check(slow)), "Regression blocks a release"
    janky = json.loads(json.dumps(ok))
    janky[0]["benchmarks"][1]["metrics"]["frameOverrunMs"]["P95"] = 3.5
    assert any("janky" in f for f in check(janky))
    assert check([{"benchmarks": []}]), "missing results block a release"
    print("check-benchmark self-test OK")


def main(args):
    if args == ["--self-test"]:
        self_test()
        return 0
    if not args:
        print(__doc__)
        return 2
    results = []
    for path in args:
        with open(path, encoding="utf-8") as f:
            results.append(json.load(f))
    failures = check(results)
    for failure in failures:
        print("FAIL:", failure)
    if not failures:
        print("Performance gate passed.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
