"""Tests for scripts/run-device-tests.py and scripts/summarize-device-results.py."""
import importlib.util
import json
import pathlib
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent


def _load(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


runner = _load("run_device_tests", "run-device-tests.py")
summary = _load("summarize_device_results", "summarize-device-results.py")


class FlowSelectionTest(unittest.TestCase):
    """Checks how flows are picked for a tier."""

    def setUp(self):
        self.dir = pathlib.Path(tempfile.mkdtemp())
        (self.dir / "a").mkdir()
        (self.dir / "a" / "smoke.yaml").write_text("# covers: x: y\nappId: app.trecos\ntags:\n  - smoke\n  - full\n---\n- back\n")
        (self.dir / "a" / "full.yaml").write_text("appId: app.trecos\ntags:\n  - full\n---\n- back\n")
        (self.dir / "a" / "untagged.yaml").write_text("appId: app.trecos\n---\n- back\n")

    def test_reads_tags_from_the_header_only(self):
        self.assertEqual(runner.flow_tags(self.dir / "a" / "smoke.yaml"), {"smoke", "full"})
        self.assertEqual(runner.flow_tags(self.dir / "a" / "untagged.yaml"), set())

    def test_pr_tier_selects_smoke_flows(self):
        names = [p.name for p in runner.select_flows(self.dir, runner.TIER_TAGS["pr"])]
        self.assertEqual(names, ["smoke.yaml"])

    def test_nightly_tier_selects_full_flows(self):
        names = [p.name for p in runner.select_flows(self.dir, runner.TIER_TAGS["nightly"])]
        self.assertEqual(names, ["full.yaml", "smoke.yaml"])


class RedactionTest(unittest.TestCase):
    """Scenario "Secrets in a failing sign-in flow": no output keeps a secret."""

    def test_runner_redacts_every_text_file(self):
        out = pathlib.Path(tempfile.mkdtemp())
        (out / "flow" / "attempt-1").mkdir(parents=True)
        log = out / "flow" / "attempt-1" / "maestro.log"
        log.write_text("inputText: hunter2-planted\nok\n")
        (out / "result.json").write_text(json.dumps({"note": "hunter2-planted"}))
        runner.redact(out, ["hunter2-planted", ""])
        for path in out.rglob("*"):
            if path.is_file():
                self.assertNotIn("hunter2-planted", path.read_text())
        self.assertIn("***", log.read_text())

    def test_runner_drops_screens_but_keeps_logs(self):
        out = pathlib.Path(tempfile.mkdtemp())
        shots = out / "flow" / "attempt-1" / "screenshots"
        shots.mkdir(parents=True)
        (shots / "step-1.png").write_bytes(b"\x89PNG")
        (out / "flow" / "attempt-1" / "recording.MP4").write_bytes(b"0")
        (out / "flow" / "attempt-1" / "maestro.log").write_text("ok\n")
        self.assertEqual(runner.drop_screens(out), 2)
        self.assertEqual([p.name for p in out.rglob("*") if p.is_file()], ["maestro.log"])

    def test_summary_redacts_secrets(self):
        results = [{"cell": "hunter2-planted", "api": 28, "memory": 2048, "flows": []}]
        text, _ = summary.summarize(results, ["hunter2-planted"])
        self.assertNotIn("hunter2-planted", text)


class SignInBlockedTest(unittest.TestCase):
    """Scenario "Google challenges the sign-in": reported, and nothing else fails."""

    def test_runner_spots_the_marker(self):
        out = pathlib.Path(tempfile.mkdtemp())
        (out / "maestro.log").write_text('Assertion is false: "sign-in-blocked" is visible\n')
        self.assertTrue(runner.sign_in_blocked(out))
        (out / "maestro.log").write_text("Element not found: Connected as.*\n")
        self.assertFalse(runner.sign_in_blocked(out))

    def test_summary_lists_it_without_failing(self):
        results = [{"cell": "android16-4g", "api": 36, "memory": 4096, "flows": [
            {"flow": "drive/connect", "status": "sign-in-blocked"}, {"flow": "items/add", "status": "passed"}]}]
        text, ok = summary.summarize(results)
        self.assertTrue(ok)
        self.assertIn("`drive/connect` sign-in-blocked", text)


class SummaryTest(unittest.TestCase):
    """Checks the merged table and its pass or fail verdict."""

    def results(self):
        return [
            {"cell": "android16-6g", "api": 36, "android": "16", "memory": 6144, "seed": 7, "flows": [
                {"flow": "app-shell/tabs", "status": "passed"}, {"flow": "items/add", "status": "flaky"}]},
            {"cell": "android9-2g", "api": 28, "android": "9", "memory": 2048, "seed": 7, "flows": [
                {"flow": "app-shell/tabs", "status": "passed"}, {"flow": "items/add", "status": "failed"}]},
        ]

    def test_failed_flow_fails_the_run_and_is_named(self):
        text, ok = summary.summarize(self.results())
        self.assertFalse(ok)
        self.assertIn("android9-2g: `items/add` failed", text)

    def test_flaky_flow_passes_but_is_reported(self):
        results = self.results()[:1]
        text, ok = summary.summarize(results)
        self.assertTrue(ok)
        self.assertIn("`items/add` flaky", text)

    def test_infrastructure_failure_is_its_own_column(self):
        """Scenario "An emulator fails to boot"."""
        results = [{"cell": "android12-3g", "api": 31, "memory": 3072, "infrastructure": True, "flows": []}] + self.results()[:1]
        text, ok = summary.summarize(results)
        self.assertFalse(ok)
        self.assertIn("| android12-3g |  | 3 GB | 0 | 0 | 0 | failure |", text)
        self.assertIn("| android16-6g | 16 | 6 GB | 1 | 0 | 1 |  |", text)

    def test_load_sorts_by_api_and_memory(self):
        root = pathlib.Path(tempfile.mkdtemp())
        for r in self.results():
            (root / r["cell"]).mkdir()
            (root / r["cell"] / "result.json").write_text(json.dumps(r))
        self.assertEqual([r["cell"] for r in summary.load(root)], ["android9-2g", "android16-6g"])


if __name__ == "__main__":
    unittest.main()


class RunnerEndToEndTest(unittest.TestCase):
    """Runs run-device-tests.py against fake adb and maestro commands."""

    def setUp(self):
        import os
        import stat
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        bin_dir = self.tmp / "bin"
        bin_dir.mkdir()
        (bin_dir / "adb").write_text('#!/bin/sh\ncase "$*" in *boot_completed*) echo 1;; *version.sdk*) echo 28;; *version.release*) echo 9;; esac\nexit 0\n')
        # Fails the first run of every flow and passes the second, counting runs in a file per flow.
        (bin_dir / "maestro").write_text(
            '#!/bin/sh\nflow="$4"; n="$FAKE_STATE/$(basename "$flow").count"\n'
            'c=$(cat "$n" 2>/dev/null || echo 0); c=$((c+1)); echo $c > "$n"\n'
            'case "$flow" in *always-fails*) echo "Element not found"; exit 1;; esac\n'
            '[ "$c" -ge 2 ] && exit 0; echo "Element not found"; exit 1\n')
        for f in bin_dir.iterdir():
            f.chmod(f.stat().st_mode | stat.S_IEXEC)
        flows = self.tmp / "flows" / "area"
        flows.mkdir(parents=True)
        for name in ("first-try-fails", "always-fails"):
            (flows / f"{name}.yaml").write_text("appId: app.trecos\ntags:\n  - smoke\n---\n- back\n")
        (self.tmp / "state").mkdir()
        self.env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}", "FAKE_STATE": str(self.tmp / "state")}

    def run_runner(self):
        import subprocess
        import sys
        done = subprocess.run([sys.executable, str(HERE / "run-device-tests.py"), "--device", "fake", "--tier", "pr", "--cell", "android9-2g",
                               "--seed", "3", "--flows", str(self.tmp / "flows"), "--out", str(self.tmp / "out")],
                              env=self.env, capture_output=True, text=True)
        return done, json.loads((self.tmp / "out" / "android9-2g" / "result.json").read_text())

    def test_flow_passing_on_rerun_is_flaky_and_a_real_failure_fails(self):
        """Scenario "A flaky flow"."""
        done, result = self.run_runner()
        statuses = {f["flow"]: f["status"] for f in result["flows"]}
        self.assertEqual(statuses, {"area/first-try-fails": "flaky", "area/always-fails": "failed"})
        self.assertEqual(done.returncode, 1)
        self.assertTrue((self.tmp / "out" / "android9-2g" / "area" / "always-fails" / "attempt-2" / "maestro.log").exists())

    def test_only_flaky_flows_pass_the_run(self):
        (self.tmp / "flows" / "area" / "always-fails.yaml").unlink()
        done, result = self.run_runner()
        self.assertEqual(done.returncode, 0)
        self.assertEqual(result["seed"], 3)

    def test_device_logs_that_are_not_utf8_do_not_crash_the_run(self):
        import stat
        (self.tmp / "bin" / "adb").write_text(
            '#!/bin/sh\ncase "$*" in *boot_completed*) echo 1;; *version.sdk*) echo 28;; '
            "*logcat\\ -d*) printf 'ok \\377\\376 bytes';; esac\nexit 0\n")
        adb = self.tmp / "bin" / "adb"
        adb.chmod(adb.stat().st_mode | stat.S_IEXEC)
        done, result = self.run_runner()
        self.assertEqual(done.returncode, 1)
        log = self.tmp / "out" / "android9-2g" / "area" / "always-fails" / "attempt-1" / "logcat.txt"
        self.assertIn("ok", log.read_text())

    def test_a_dead_maestro_driver_is_retried_without_counting(self):
        import os
        import stat
        (self.tmp / "flows" / "area" / "always-fails.yaml").unlink()
        (self.tmp / "flows" / "area" / "first-try-fails.yaml").rename(self.tmp / "flows" / "area" / "driver.yaml")
        (self.tmp / "bin" / "maestro").write_text(
            '#!/bin/sh\nn="$FAKE_STATE/driver.count"; c=$(cat "$n" 2>/dev/null || echo 0); c=$((c+1)); echo $c > "$n"\n'
            '[ "$c" -ge 2 ] && exit 0; echo "DeviceServerDiedException: UNAVAILABLE"; exit 1\n')
        maestro = self.tmp / "bin" / "maestro"
        maestro.chmod(maestro.stat().st_mode | stat.S_IEXEC)
        done, result = self.run_runner()
        self.assertEqual(done.returncode, 0)
        self.assertEqual([(f["status"], f["attempts"]) for f in result["flows"]], [("passed", 1)])

    def test_missing_device_is_an_infrastructure_failure(self):
        """Scenario "An emulator fails to boot"."""
        (self.tmp / "bin" / "adb").write_text("#!/bin/sh\nexit 1\n")
        done, result = self.run_runner()
        self.assertEqual(done.returncode, 2)
        self.assertTrue(result["infrastructure"])
        self.assertEqual(result["flows"], [])
