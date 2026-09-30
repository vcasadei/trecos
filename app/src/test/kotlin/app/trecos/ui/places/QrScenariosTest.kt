package app.trecos.ui.places

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import app.trecos.places.Label
import app.trecos.places.Selection
import app.trecos.ui.search.SEARCH_FIELD_TAG
import app.trecos.ui.shell.TrecosApp
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.tabTag
import app.trecos.ui.theme.TrecosTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenarios of the qr-codes spec (tasks 8.1 and 8.3-8.6), with the scan in
 * "Scanning offline", "Scanning a trashed box" and "Cancelling a scan". The
 * system print dialog and the Play services scanner can't run in tests, so
 * fakes record the print jobs and return the scan results.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class QrScenariosTest : PlacesTestBase() {

    /** Records each print job. */
    private class FakePrinter : LabelPrinter {
        val jobs = mutableListOf<List<Label>>()

        override fun print(context: Context, labels: List<Label>) {
            jobs += labels
        }
    }

    /** Returns [next] and counts the scans. */
    private class FakeScanner : QrScanner {
        var next: ScanResult = ScanResult.Cancelled
        var scans = 0

        override suspend fun scan(context: Context): ScanResult {
            scans++
            return next
        }
    }

    private val printer = FakePrinter()
    private val scanner = FakeScanner()

    @Before
    fun useFakes() {
        rule.runOnUiThread {
            rule.activity.setContent {
                CompositionLocalProvider(LocalLabelPrinter provides printer, LocalQrScanner provides scanner) { TrecosTheme { TrecosApp() } }
            }
        }
    }

    /** @return whether a node with the tag exists. */
    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Waits until some node shows the text, such as the title of the screen that opened.
     *
     * @param name the text.
     */
    private fun shown(name: String) =
        eventually(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasText(name), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    /** Opens the new-container form on the house's top level. */
    private fun newContainer() {
        click(ADD_BUTTON_TAG)
        text("Container").performClick()
        rule.waitForIdle()
    }

    /** Opens the new-item form on the house's top level. */
    private fun newItem() {
        click(ADD_BUTTON_TAG)
        text("Item").performClick()
        rule.waitForIdle()
    }

    /** @return the house's top-level containers. */
    private fun topContainers() = runBlocking { app.database.containers().observeChildren("h1", null).first() }

    /**
     * Scans from the Search tab.
     *
     * @param result what the fake scanner returns.
     */
    private fun scanFromSearch(result: ScanResult) {
        scanner.next = result
        click(tabTag(TrecosTab.Search))
        click("scan_qr")
    }

    @Test
    fun codeFollowsTheNameAtCreation() {
        seed()
        newContainer()
        type("name", "Armario#1")
        saveAndClose()

        assertEquals("Armario#1", topContainers().single().qrCode)
    }

    @Test
    fun renameKeepsTheLabelValid() {
        seed(containers = listOf(container("armario", name = "Armario#1").copy(qrCode = "Armario#1")))
        runBlocking {
            val armario = app.database.containers().get("armario")!!
            app.database.containers().update(armario.copy(name = "Wardrobe"))
        }
        scanFromSearch(ScanResult.Code("Armario#1"))

        shown("Wardrobe")
    }

    @Test
    fun nameAlreadyUsedAsACode() {
        seed(containers = listOf(container("box1", name = "Box 1").copy(qrCode = "Box 1")))
        newContainer()
        type("name", "Box 1")
        saveAndClose()

        text("The code \"Box 1\" is already used in this house, so this one has no QR code.")
        val second = topContainers().single { it.id != "box1" }
        assertNull(second.qrCode)
    }

    @Test
    fun enteringATakenCode() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi", qrCode = "PI-1"), item("mouse").copy(name = "Mouse")))
        click(rowTag("mouse"))
        clickDescription("Edit")
        waitForField("name", "Mouse")
        click("more_fields")
        type("qr", "PI-1")
        click("save")

        tag("error_qr").assertTextContains("Raspberry Pi", substring = true)
        assertNull(runBlocking { app.database.items().get("mouse")?.qrCode })
        click("open_holder")
        shown("Raspberry Pi")
    }

    @Test
    fun printingOneLabel() {
        seed(containers = listOf(container("boxA", name = "Box A").copy(qrCode = "Box A")))
        click(rowTag("boxA"))
        click("show_qr")
        tag("qr_label").assertIsDisplayed()
        click("print_label")

        assertEquals(listOf(listOf(Label("Box A"))), printer.jobs)
    }

    @Test
    fun printingSeveralLabels() {
        seed(containers = (1..6).map { container("c$it", name = "Box $it").copy(qrCode = "Box $it") })
        tag(rowTag("c1")).performTouchInput { longClick() }
        clickDescription("Select all")
        clickDescription("Print QR")

        assertEquals(1, printer.jobs.size)
        assertEquals(6, printer.jobs.single().size)
    }

    @Test
    fun nothingToPrint() {
        seed(
            containers = listOf(
                container("a", name = "Box A").copy(qrCode = "Box A"),
                container("b", name = "Box B").copy(qrCode = "Box B"),
                container("c", name = "Box C"),
            ),
        )
        tag(rowTag("a")).performTouchInput { longClick() }
        clickDescription("Select all")
        clickDescription("Print QR")

        assertEquals(listOf("Box A", "Box B"), printer.jobs.single().map { it.code })
        text("1 had no QR code and was skipped.")
    }

    @Test
    fun labelFromAnotherProgram() {
        seed(items = listOf(item("tape").copy(name = "Label tape", qrCode = "XYZ-0042")))
        scanFromSearch(ScanResult.Code("XYZ-0042"))

        shown("Label tape")
    }

    @Test
    fun lookupIsCaseSensitive() {
        seed(items = listOf(item("tape").copy(name = "Label tape", qrCode = "XYZ-0042")))
        scanFromSearch(ScanResult.Code("xyz-0042"))

        tag("unknown_code")
    }

    @Test
    fun scanningOffline() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        scanFromSearch(ScanResult.Unavailable)

        text("Scanning needs a one-time download over the internet. You can still type a code into search.")
        type("search_field_raw", "Raspberry")
        text("Raspberry Pi")
    }

    @Test
    fun oneMatch() {
        seed(
            houses = listOf(house("h1", "Apartment"), house("h2", "Parents")),
            containers = listOf(container("boxA", name = "Box A").copy(qrCode = "BOX-A")),
        )
        scanFromSearch(ScanResult.Code("BOX-A"))

        shown("Box A")
    }

    @Test
    fun severalHouses() {
        seed(
            houses = listOf(house("h1", "Apartment"), house("h2", "Parents")),
            containers = listOf(
                container("a1", name = "Box 1").copy(qrCode = "Box 1"),
                container("p1", name = "Parents box", houseId = "h2").copy(qrCode = "Box 1"),
            ),
        )
        scanFromSearch(ScanResult.Code("Box 1"))

        click("choose_h2")
        shown("Parents box")
    }

    @Test
    fun unknownCode() {
        seed(containers = listOf(container("boxA", name = "Box A")))
        scanFromSearch(ScanResult.Code("XYZ-0042"))

        click("create_item")
        tag("destination_picker")
        click(destTag("boxA"))
        click("dest_confirm")
        waitForField("name", "XYZ-0042")
        waitForField("qr", "XYZ-0042")
        saveAndClose()

        val saved = runBlocking { app.database.items().observeIn("h1", "boxA").first() }.single()
        assertEquals("XYZ-0042", saved.qrCode)
        assertEquals("XYZ-0042", saved.name)
    }

    @Test
    fun scanningATrashedBox() {
        seed(containers = listOf(container("boxC", name = "Box C").copy(qrCode = "BOX-C")))
        runBlocking { app.organize.trash(Selection(containerIds = listOf("boxC"))) }
        scanFromSearch(ScanResult.Code("BOX-C"))

        tag("trashed_notice").assertTextContains("\"Box C\" is in the trash.")
        click("scan_restore")
        shown("Box C")
        assertNull(runBlocking { app.database.containers().get("boxC")?.deletedAt })
    }

    @Test
    fun scanningIntoAnEmptyForm() {
        seed()
        newItem()
        scanner.next = ScanResult.Code("  Armario#1 ")
        click("more_fields")
        click("form_scan")

        waitForField("qr", "Armario#1")
        waitForField("name", "Armario#1")
    }

    @Test
    fun scanningWhenANameExists() {
        seed()
        newItem()
        type("name", "Wardrobe")
        scanner.next = ScanResult.Code("Armario#1")
        click("more_fields")
        click("form_scan")

        waitForField("qr", "Armario#1")
        waitForField("name", "Wardrobe")
    }

    @Test
    fun cancellingAScan() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        click(tabTag(TrecosTab.Search))
        type("search_field_raw", "Raspberry")
        text("Raspberry Pi")
        scanner.next = ScanResult.Cancelled
        click("scan_qr")

        assertEquals(1, scanner.scans)
        tag(SEARCH_FIELD_TAG).assertTextContains("Raspberry")
        text("Raspberry Pi").assertIsDisplayed()
        assertTrue(!exists("unknown_code"))
    }
}
