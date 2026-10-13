"""Tests for scripts/device-matrix.py (spec device-testing, "Device matrix" and "Run tiers")."""
import importlib.util
import json
import pathlib
import subprocess
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("device_matrix", HERE / "device-matrix.py")
device_matrix = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(device_matrix)


class DeviceMatrixTest(unittest.TestCase):
    """Checks the cells each tier runs on."""

    def test_pr_runs_the_two_corners(self):
        cells = device_matrix.matrix("pr", day=1)
        self.assertEqual([(c["api"], c["memory"]) for c in cells], [(28, 2048), (36, 6144)])

    def test_weekly_and_release_run_all_36_cells(self):
        for tier in ("weekly", "release"):
            cells = device_matrix.matrix(tier, day=1)
            self.assertEqual(len(cells), 36)
            self.assertEqual(len({(c["api"], c["memory"]) for c in cells}), 36)

    def test_nightly_runs_each_android_version_once(self):
        cells = device_matrix.matrix("nightly", day=100)
        self.assertEqual([c["api"] for c in cells], list(range(28, 37)))

    def test_nightly_rotates_memory_within_a_week(self):
        """Scenario "The nightly run rotates memory"."""
        for start in (1, 200, 362):
            seen = {api: set() for api in range(28, 37)}
            for day in range(start, start + 7):
                for c in device_matrix.matrix("nightly", day=day):
                    seen[c["api"]].add(c["memory"])
            for api, sizes in seen.items():
                self.assertEqual(sizes, set(device_matrix.MEMORY_MB), f"API {api} from day {start}")

    def test_cells_name_their_android_version_and_memory(self):
        cell = device_matrix.cell(28, 2048)
        self.assertEqual(cell, {"api": 28, "android": "9", "memory": 2048, "name": "android9-2g"})

    def test_drive_job_never_runs_for_pull_requests(self):
        self.assertEqual(device_matrix.drive_matrix("pr"), [])

    def test_drive_job_runs_api_36_nightly_and_adds_the_reference_weekly(self):
        self.assertEqual([c["name"] for c in device_matrix.drive_matrix("nightly")], ["drive-android16-4g"])
        for tier in ("weekly", "release"):
            self.assertEqual([c["name"] for c in device_matrix.drive_matrix(tier)], ["drive-android9-2g", "drive-android16-4g"])

    def test_unknown_tier_is_refused(self):
        with self.assertRaises(ValueError):
            device_matrix.matrix("hourly", day=1)

    def test_command_line_prints_a_github_matrix(self):
        out = subprocess.run([sys.executable, str(HERE / "device-matrix.py"), "pr"], capture_output=True, text=True, check=True).stdout
        self.assertEqual(len(json.loads(out)["include"]), 2)


if __name__ == "__main__":
    unittest.main()
