package app.trecos.ui.places

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import app.trecos.R
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import app.trecos.places.Selection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenarios of the organize and trash specs in the app (tasks 5.1-5.4, 5.6, 5.7, 5.9, 5.10).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class OrganizeScenariosTest : PlacesTestBase() {

    private val boxA = container("boxA", name = "Box A").copy(qrCode = "BOX-A")
    private val cables = container("cables", parentId = "boxA", name = "Cables bag")
    private val drawer = container("drawer", name = "Office drawer")
    private val pi = item("pi", containerId = "boxA").copy(name = "Raspberry Pi 4")

    /** Opens the overflow menu and taps an entry. */
    private fun menu(label: Int) {
        clickDescription("More options")
        click("menu_$label")
    }

    /** @return the container of an item. */
    private fun containerOf(itemId: String) = runBlocking { app.database.items().get(itemId)?.containerId }

    /** @return whether a node with the tag exists. */
    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Long-presses a row. */
    private fun longPress(id: String) {
        tag(rowTag(id)).performTouchInput { longClick() }
        rule.waitForIdle()
    }

    @Test
    fun movingAnItem() {
        seed(containers = listOf(boxA, drawer), items = listOf(pi))
        click(rowTag("boxA"))
        click(rowTag("pi"))
        menu(R.string.action_move)
        click(destTag("drawer"))
        click("dest_confirm")

        rule.waitUntil(10_000) { containerOf("pi") == "drawer" }
        assertTrue(runBlocking { app.database.items().get("pi")!!.updatedAt } > pi.updatedAt)
    }

    @Test
    fun movingAContainerIntoItsOwnChild() {
        seed(containers = listOf(boxA, cables, drawer))
        click(rowTag("boxA"))
        menu(R.string.action_move)
        click(destTag("boxA"))

        tag("dest_blocked").assertIsDisplayed()
        tag("dest_confirm").assertIsNotEnabled()
        click(destTag("cables"))
        tag("dest_confirm").assertIsNotEnabled()
    }

    @Test
    fun movingToAnotherHouseWithAQrClash() {
        seed(
            houses = listOf(house("h1", "Apartment"), house("h2", "Parents")),
            items = listOf(item("drill").copy(name = "Drill", qrCode = "Drill-01"), item("other", houseId = "h2").copy(qrCode = "Drill-01")),
        )
        click(rowTag("drill"))
        menu(R.string.action_move)
        click("dest_house")
        click("dest_house_h2")
        click("dest_confirm")

        tag("qr_clash").assertIsDisplayed()
        assertEquals("h1", runBlocking { app.database.items().get("drill")!!.houseId })
        click("clash_remove")

        rule.waitUntil(10_000) { runBlocking { app.database.items().get("drill")?.houseId } == "h2" }
        assertNull(runBlocking { app.database.items().get("drill")!!.qrCode })
    }

    @Test
    fun cancellingAClashMovesNothing() {
        seed(
            houses = listOf(house("h1", "Apartment"), house("h2", "Parents")),
            items = listOf(item("drill").copy(qrCode = "Drill-01"), item("other", houseId = "h2").copy(qrCode = "Drill-01")),
        )
        click(rowTag("drill"))
        menu(R.string.action_move)
        click("dest_house")
        click("dest_house_h2")
        click("dest_confirm")
        click("clash_cancel")

        assertEquals("h1", runBlocking { app.database.items().get("drill")!!.houseId })
    }

    @Test
    fun copyingALabelledBox() {
        seed(containers = listOf(boxA, drawer), items = (1..12).map { item("i$it", containerId = "boxA").copy(qrCode = "I-$it") })
        click(rowTag("boxA"))
        menu(R.string.action_copy)
        click(destTag("drawer"))
        click("dest_confirm")

        rule.waitUntil(10_000) {
            runBlocking {
                app.database.containers().observeChildren("h1", "drawer").first().singleOrNull()
                    ?.let { app.database.items().observeIn("h1", it.id).first().size } == 12
            }
        }
        val copy = runBlocking { app.database.containers().observeChildren("h1", "drawer").first().single() }
        assertNull(copy.qrCode)
        val copied = runBlocking { app.database.items().observeIn("h1", copy.id).first() }
        assertEquals(12, copied.size)
        assertTrue(copied.all { it.qrCode == null })
        assertEquals("BOX-A", runBlocking { app.database.containers().get("boxA")!!.qrCode })
    }

    @Test
    fun duplicatingAnItem() {
        seed(containers = listOf(cables.copy(parentId = null)), items = listOf(item("usb", containerId = "cables").copy(name = "USB-C cable 1m")))
        click(rowTag("cables"))
        click(rowTag("usb"))
        menu(R.string.action_duplicate)

        tag(fieldTag("name")).assertTextContains("USB-C cable 1m (copy)")
        val inBag = runBlocking { app.database.items().observeIn("h1", "cables").first().map { it.name } }
        assertEquals(listOf("USB-C cable 1m", "USB-C cable 1m (copy)"), inBag)
    }

    @Test
    fun movingSeveralItems() {
        seed(containers = listOf(boxA, drawer), items = listOf(item("a", containerId = "boxA"), item("b", containerId = "boxA"), item("c", containerId = "boxA")))
        click(rowTag("boxA"))
        longPress("a")
        click(rowTag("b"))
        click(rowTag("c"))
        tag("selected_count").assertTextContains("3 selected")

        clickDescription("Move")
        click(destTag("drawer"))
        click("dest_confirm")

        rule.waitUntil(10_000) { listOf("a", "b", "c").all { containerOf(it) == "drawer" } }
        rule.waitUntil(10_000) { !exists("selection_bar") }
    }

    @Test
    fun leavingSelection() {
        seed(containers = listOf(boxA), items = listOf(item("a", containerId = "boxA"), item("b", containerId = "boxA")))
        click(rowTag("boxA"))
        longPress("a")
        click(rowTag("b"))
        tag("selection_bar")

        pressBack()

        assertTrue(!exists("selection_bar"))
        tag(rowTag("a")).assertIsDisplayed()
        assertEquals(listOf("boxA", "boxA"), listOf(containerOf("a"), containerOf("b")))
    }

    @Test
    fun deletingAnItemAsksFirstAndCancelling() {
        seed(containers = listOf(boxA), items = listOf(item("hub", containerId = "boxA").copy(name = "USB hub")))
        click(rowTag("boxA"))
        click(rowTag("hub"))
        menu(R.string.action_delete)

        tag("delete_title").assertTextContains("USB hub", substring = true)
        assertTrue(runBlocking { app.database.items().get("hub") } != null)
        click("cancel_delete")
        assertTrue(runBlocking { app.database.items().get("hub") } != null)
    }

    @Test
    fun undoingADelete() {
        seed(containers = listOf(boxA), items = listOf(item("hub", containerId = "boxA").copy(name = "USB hub")))
        click(rowTag("boxA"))
        click(rowTag("hub"))
        menu(R.string.action_delete)
        click("confirm_delete")

        rule.waitUntil(10_000) { runBlocking { app.database.items().get("hub") } == null }
        text("Undo").performClick()

        rule.waitUntil(10_000) { runBlocking { app.database.items().get("hub") } != null }
        assertEquals("boxA", containerOf("hub"))
    }

    @Test
    fun trashScreenRestoresAndDeletesPermanently() {
        seed(containers = listOf(boxA, cables), items = listOf(item("usb", containerId = "cables").copy(name = "USB-C cable")))
        runBlocking { app.organize.trash(Selection(containerIds = listOf("cables"))) }
        clickDescription("More options")
        click("menu_trash")
        tag(trashTag("Cables bag"))

        click("restore_Cables bag")

        rule.waitUntil(10_000) { runBlocking { app.database.containers().get("cables") } != null && containerOf("usb") != null }
        assertEquals("boxA", runBlocking { app.database.containers().get("cables")!!.parentId })
        assertEquals("cables", containerOf("usb"))
        tag("trash_empty")
    }

    @Test
    fun originalLocationGone() {
        seed(containers = listOf(boxA, cables, drawer), items = listOf(item("usb", containerId = "cables").copy(name = "USB-C cable")))
        runBlocking {
            app.organize.trash(Selection(itemIds = listOf("usb")))
            app.organize.deletePermanently(app.organize.trash(Selection(containerIds = listOf("cables"))).single().let { it })
        }
        clickDescription("More options")
        click("menu_trash")
        click("restore_USB-C cable")

        tag("destination_picker")
        click(destTag("drawer"))
        click("dest_confirm")
        rule.waitUntil(10_000) { containerOf("usb") == "drawer" }
    }

    @Test
    fun keepingSomeThings() {
        seed(
            containers = listOf(boxA, cables, container("garage", name = "Garage shelf"), drawer),
            items = listOf(pi, item("mouse", containerId = "boxA").copy(name = "Broken mouse")),
        )
        click(rowTag("boxA"))
        menu(R.string.action_delete)
        click("choose_what_to_keep")

        click("keep_check_Cables bag")
        click("move_selected")
        click("keep_choose")
        click(destTag("garage"))
        click("dest_confirm")
        rule.waitUntil(10_000) { runBlocking { app.database.containers().get("cables")?.parentId } == "garage" }

        click("keep_check_Raspberry Pi 4")
        click("move_selected")
        click("keep_choose")
        click(destTag("drawer"))
        click("dest_confirm")
        rule.waitUntil(10_000) { containerOf("pi") == "drawer" }

        click("keep_finish")
        click("confirm_finish")
        rule.waitUntil(10_000) { runBlocking { app.database.organize().observeTrash("h1").first().size == 1 } }
        assertNull(runBlocking { app.database.containers().get("boxA") })
        assertNull(runBlocking { app.database.items().get("mouse") })
        assertEquals("garage", runBlocking { app.database.containers().get("cables")!!.parentId })
    }

    @Test
    fun leavingTheKeepScreenEarly() {
        seed(containers = listOf(boxA, cables, drawer), items = listOf(pi))
        click(rowTag("boxA"))
        menu(R.string.action_delete)
        click("choose_what_to_keep")
        click("keep_check_Raspberry Pi 4")
        click("move_selected")
        click("keep_parent")
        rule.waitUntil(10_000) { containerOf("pi") == null }

        pressBack()

        assertTrue(runBlocking { app.database.containers().get("boxA") } != null)
        assertNull(containerOf("pi"))
    }

    @Test
    fun deletingAHouse() {
        seed(houses = listOf(house("beach", "Beach house"), house("h1", "Apartment")), items = listOf(item("towel", houseId = "beach")))
        clickDescription("More options")
        click("menu_delete")
        tag("type_house_name").performTextInput("Beach house")
        click("confirm_delete_house")

        rule.waitUntil(10_000) { runBlocking { app.database.houses().get("beach") } == null }
        assertNull(runBlocking { app.database.organize().itemAnyState("towel") })
        text("Apartment")
    }

    @Test
    fun wrongNameTyped() {
        seed(houses = listOf(house("beach", "Beach house"), house("h1", "Apartment")))
        clickDescription("More options")
        click("menu_delete")
        tag("type_house_name").performTextInput("Beach")

        tag("confirm_delete_house").assertIsNotEnabled()
        rule.onNode(hasText("Beach house", substring = true)).assertIsDisplayed()
    }
}
