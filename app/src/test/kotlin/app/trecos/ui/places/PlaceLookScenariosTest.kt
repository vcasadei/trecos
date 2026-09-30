package app.trecos.ui.places

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performClick
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import app.trecos.data.HouseBand
import app.trecos.data.ListView
import app.trecos.ui.shell.HOUSE_BAND_TAG
import app.trecos.ui.text.HOUSE_PILL_TAG
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenarios of the places "Container value", "Colours", "House indicator" and
 * "Icons for places without photos" requirements, and app-shell "List presentation".
 * Robolectric reports a status bar of height 0, so the band's presence is checked, not its size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class PlaceLookScenariosTest : PlacesTestBase() {

    /** @return whether any node with the tag exists. */
    private fun exists(tag: String) =
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * @param rowId the row's record id.
     * @return whether that row shows a colour stripe.
     */
    private fun hasStripe(rowId: String) =
        rule.onAllNodes(hasTestTag(STRIPE_TAG).and(androidx.compose.ui.test.hasAnyAncestor(hasTestTag(rowTag(rowId)))), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun overrideCountsUpwardAndClears() {
        seed(
            containers = listOf(
                container("office", name = "Office"),
                container("boxA", parentId = "office", name = "Box A"),
                container("boxB", parentId = "office", name = "Box B").copy(valueOverride = 50_000),
            ),
            items = listOf(
                item("laptop", containerId = "office", unitPrice = 350_000),
                item("cable", containerId = "boxA", unitPrice = 22_000),
                item("mystery", containerId = "boxB"),
            ),
        )
        runBlocking { app.preferences.setCurrency("BRL") }
        click(rowTag("office"))
        waitForTextIn("place_value", "R$4,220.00 · automatic")

        click(rowTag("boxB"))
        waitForTextIn("place_value", "R$500.00 · manual")
        tag("unpriced_hint").assertTextContains("1 item has no price")

        click("clear_override")
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("R$0.00 · automatic", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(null, runBlocking { app.database.containers().get("boxB")?.valueOverride })
    }

    @Test
    fun inheritedColour() {
        seed(containers = listOf(container("office", name = "Office").copy(colorKey = "blue"), container("boxA", parentId = "office", name = "Box A")))
        click(rowTag("office"))
        tag(rowTag("boxA"))
        assertTrue("Box A inherits Office's stripe", hasStripe("boxA"))
    }

    @Test
    fun ownColourWins() {
        seed(
            containers = listOf(
                container("office", name = "Office").copy(colorKey = "blue"),
                container("boxA", parentId = "office", name = "Box A"),
                container("boxB", parentId = "office", name = "Box B").copy(colorKey = "coral"),
                container("plain", name = "Plain"),
            ),
        )
        tag(rowTag("plain"))
        assertTrue(!hasStripe("plain"))
        click(rowTag("office"))
        tag(rowTag("boxB"))
        assertTrue(hasStripe("boxA") && hasStripe("boxB"))
    }

    @Test
    fun twoHouses() {
        seed(
            houses = listOf(house("parents", "Parents").copy(colorKey = "rose"), house("apartment", "Apartment").copy(colorKey = "mint")),
            containers = listOf(container("sala", name = "Sala", houseId = "parents")),
        )
        tag(HOUSE_BAND_TAG)
        click(rowTag("sala"))
        tag(HOUSE_PILL_TAG).assertTextContains("Parents")
    }

    @Test
    fun singleHouse() {
        seed(containers = listOf(container("office", name = "Office")))
        click(rowTag("office"))
        tag(rowTag("office").let { EMPTY_HINT_TAG })
        assertTrue(!exists(HOUSE_BAND_TAG))
        assertTrue(!exists(HOUSE_PILL_TAG))
    }

    @Test
    fun bandSettingCanForceItOnAndOff() {
        seed()
        runBlocking { app.preferences.setHouseBand(HouseBand.Always) }
        tag(HOUSE_BAND_TAG)

        runBlocking {
            app.database.houses().insert(house("h2", "Parents"))
            app.preferences.setHouseBand(HouseBand.Never)
        }
        rule.waitUntil(10_000) { !exists(HOUSE_BAND_TAG) }
    }

    @Test
    fun containerWithoutPhotos() {
        seed(containers = listOf(container("drawer2", name = "Drawer 2").copy(icon = "drawer")))
        tag(badgeTag("drawer")).assertIsDisplayed()
    }

    @Test
    fun switchingToDetailedView() {
        seed(containers = listOf(container("office", name = "Office").copy(description = "The home office")))
        tag(rowTag("office"))
        assertTrue(rule.onAllNodes(hasText("The home office"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        click(VIEW_TOGGLE_TAG)

        text("The home office").assertIsDisplayed()
        rule.waitUntil(PREFERENCE_WRITE_MS) { runBlocking { app.preferences.listView.first() } == ListView.Detailed }

        rule.activityRule.scenario.recreate()
        text("The home office").assertIsDisplayed()
    }

    @Test
    fun itemWithoutPrice() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi"), item("mouse", unitPrice = 9_900).copy(name = "Mouse")))
        runBlocking { app.preferences.setListView(ListView.Detailed) }

        tag(rowTag("pi"))
        rule.onNode(hasTestTag(rowTag("mouse"))).assertTextContains("Unit price", substring = true)
        val piTexts = rule.onNode(hasTestTag(rowTag("pi"))).fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString()
        assertTrue("no price on an unpriced row: $piTexts", !piTexts.contains("Unit price"))
    }
}
