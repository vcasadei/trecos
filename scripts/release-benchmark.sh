#!/usr/bin/env bash
# Runs the release benchmark on the booted reference emulator and gates it
# (spec device-testing, "Reference emulator and performance gate").
# Usage: scripts/release-benchmark.sh check|record
#   check:  fails when the median cold start is more than 15% slower than
#           benchmark/reference-baseline.json for this runner and image.
#   record: writes a new benchmark/reference-baseline.json instead.
# The numbers go to $GITHUB_STEP_SUMMARY when it is set.
set -euo pipefail
mode="${1:?check or record}"
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
mapfile -t results < <(find baselineprofile/build/outputs/connected_android_test_additional_output -name '*benchmarkData.json' | sort)
runner="${ImageOS:-local} $(nproc) cores"
image="$(adb shell getprop ro.build.fingerprint | tr -d '\r')"
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
if [ "$mode" = record ]; then
  python3 scripts/check-benchmark.py --record benchmark/reference-baseline.json --runner "$runner" --image "$image" "${results[@]}"
  # The summary then shows the numbers against the baseline just recorded.
  python3 scripts/check-benchmark.py --baseline benchmark/reference-baseline.json --runner "$runner" --image "$image" \
    --summary "$summary" "${results[@]}"
else
  python3 scripts/check-benchmark.py --baseline benchmark/reference-baseline.json --runner "$runner" --image "$image" \
    --summary "$summary" "${results[@]}"
fi
