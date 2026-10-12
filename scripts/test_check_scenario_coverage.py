"""Tests for scripts/check-scenario-coverage.py (spec device-testing, "Scenario coverage")."""
import importlib.util
import pathlib
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("check_scenario_coverage", HERE / "check-scenario-coverage.py")
coverage = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(coverage)

TRECOS_APP = """
NavHost(navController, startDestination = rootRoute(TrecosTab.Home)) {
    composable(rootRoute(TrecosTab.Search)) { SearchScreen(nav) }
    composable(Routes.ITEM_FORM, listOf(optionalArg("id"))) { entry -> ItemForm() }
}
"""


class CoverageTest(unittest.TestCase):
    """Builds a small fake repository and checks what the script reports."""

    def setUp(self):
        self.repo = pathlib.Path(tempfile.mkdtemp())
        spec = self.repo / "openspec" / "changes" / "trecos-v1" / "specs" / "items"
        spec.mkdir(parents=True)
        spec.joinpath("spec.md").write_text("### Requirement: Items\n#### Scenario: Adding an item\n#### Scenario: Item without a name\n")
        shell = self.repo / "app" / "src" / "main" / "kotlin" / "app" / "trecos" / "ui" / "shell"
        shell.mkdir(parents=True)
        shell.joinpath("TrecosApp.kt").write_text(TRECOS_APP)
        flows = self.repo / "maestro" / "flows" / "items"
        flows.mkdir(parents=True)
        flows.joinpath("add.yaml").write_text(
            "# covers: items: Adding an item\n# screens: search, item_form\nappId: app.trecos\n---\n# covers: items: Not a header\n- back\n")
        (self.repo / "maestro" / "COVERAGE.md").write_text(
            "| Scenario | Covered by | Reason |\n|---|---|---|\n| items: Item without a name | Robolectric only | Form validation, no device behaviour |\n")

    def test_everything_covered_passes(self):
        self.assertEqual(coverage.check(self.repo), ([], []))

    def test_new_scenario_with_no_flow_is_named(self):
        """Scenario "A new scenario with no flow"."""
        spec = self.repo / "openspec" / "changes" / "trecos-v1" / "specs" / "items" / "spec.md"
        spec.write_text(spec.read_text() + "#### Scenario: Planted scenario\n")
        self.assertEqual(coverage.check(self.repo), (["items: Planted scenario"], []))

    def test_screen_with_no_flow_is_named(self):
        """Scenario "A screen with no flow"."""
        app = self.repo / "app" / "src" / "main" / "kotlin" / "app" / "trecos" / "ui" / "shell" / "TrecosApp.kt"
        app.write_text(app.read_text().replace("}\n", "    composable(Routes.PLANTED_SCREEN) { }\n}\n", 1))
        self.assertEqual(coverage.check(self.repo)[1], ["planted_screen"])

    def test_listing_without_a_reason_does_not_count(self):
        md = self.repo / "maestro" / "COVERAGE.md"
        md.write_text(md.read_text().replace("Form validation, no device behaviour", ""))
        self.assertEqual(coverage.check(self.repo)[0], ["items: Item without a name"])

    def test_covers_lines_after_the_header_are_ignored(self):
        covers, _ = coverage.flow_headers(self.repo / "maestro" / "flows")
        self.assertEqual(covers, {"items: Adding an item"})

    def test_device_test_listing_counts(self):
        spec = self.repo / "openspec" / "changes" / "trecos-v1" / "specs" / "items" / "spec.md"
        spec.write_text(spec.read_text() + "#### Scenario: Stored without location\n")
        md = self.repo / "maestro" / "COVERAGE.md"
        md.write_text(md.read_text() + "| items: Stored without location | device test | `PhotoDeviceTest` reads the stored file |\n")
        self.assertEqual(coverage.check(self.repo), ([], []))

    def test_main_specs_count_too(self):
        main = self.repo / "openspec" / "specs" / "search"
        main.mkdir(parents=True)
        main.joinpath("spec.md").write_text("#### Scenario: Searching\n")
        self.assertEqual(coverage.check(self.repo)[0], ["search: Searching"])


if __name__ == "__main__":
    unittest.main()
