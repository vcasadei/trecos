package app.trecos.ui.places

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import app.trecos.ui.shell.HOUSE_BAND_TAG
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scenarios of the places "Houses", "At least one house" and "Home tab entry point" requirements.
 */
@RunWith(RobolectricTestRunner::class)
class HouseScenariosTest : PlacesTestBase() {

    @Test
    fun firstRun() {
        tag(FIRST_RUN_TAG).assertIsDisplayed()
        tag(fieldTag("name")).assertTextContains("My home")

        click("continue")

        tag(EMPTY_HINT_TAG).assertIsDisplayed()
        text("My home").assertIsDisplayed()
        val houses = runBlocking { app.database.houses().observeAll().first() }
        assertEquals(listOf("My home"), houses.map { it.name })
        eventually(PREFERENCE_WRITE_MS) { runBlocking { app.preferences.lastHouseId.first() } == houses.first().id }
    }

    @Test
    fun creatingASecondHouse() {
        seed()
        text("Apartment").performClickAndIdle()
        click("switch_add_house")
        type("name", "Casa dos meus pais")
        type("address", "Rua das Flores, 10")
        saveAndClose()

        tag(EMPTY_HINT_TAG).assertIsDisplayed()
        text("Casa dos meus pais").assertIsDisplayed()
        val houses = runBlocking { app.database.houses().observeAll().first() }
        assertEquals(listOf("Apartment", "Casa dos meus pais"), houses.map { it.name })
        val added = houses.last()
        assertEquals(PlaceIcons.defaultHouse, added.icon)
        assertEquals("Rua das Flores, 10", added.address)
    }

    @Test
    fun houseWithoutAName() {
        seed()
        text("Apartment").performClickAndIdle()
        click("switch_add_house")
        type("name", "   ")
        click("save")

        tag("error_name").assertIsDisplayed()
        assertEquals(1, runBlocking { app.database.houses().count() })
    }

    @Test
    fun deletingTheLastHouse() {
        seed()
        tag(EMPTY_HINT_TAG)
        clickDescription("More options")
        click("menu_delete")

        tag("explanation").assertTextContains("At least one house is required", substring = true)
        assertEquals(1, runBlocking { app.database.houses().count() })
    }

    @Test
    fun singleHouse() {
        seed(containers = listOf(container("office", name = "Office")))

        tag(rowTag("office")).assertIsDisplayed()
        text("Apartment").assertIsDisplayed()
    }

    @Test
    fun severalHouses() {
        seed(houses = listOf(house("h1", "Apartment"), house("h2", "Parents")))
        showHouseList()

        tag(rowTag("h1")).assertIsDisplayed()
        click(rowTag("h2"))

        tag(EMPTY_HINT_TAG).assertIsDisplayed()
        text("Parents").assertIsDisplayed()
        eventually(PREFERENCE_WRITE_MS) { runBlocking { app.preferences.lastHouseId.first() } == "h2" }

        pressBack()
        tag(HOUSE_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun houseListShowsItemCountsAndValues() {
        seed(
            houses = listOf(house("h1", "Apartment"), house("h2", "Parents")),
            containers = listOf(container("office", name = "Office")),
            items = listOf(item("cable", containerId = "office", quantity = 2, unitPrice = 1_000), item("lamp", unitPrice = 500)),
        )
        showHouseList()

        rowShows("h1", "2 items")
        rowShows("h1", "25.00")
        rowShows("h2", "0 items")
    }

    @Test
    fun addingAHouseFromTheList() {
        seed(houses = listOf(house("h1", "Apartment"), house("h2", "Parents")))
        showHouseList()
        click(HOUSE_LIST_ADD_TAG)
        type("name", "Beach house")
        saveAndClose()

        tag(EMPTY_HINT_TAG).assertIsDisplayed()
        text("Beach house").assertIsDisplayed()
        pressBack()
        tag(rowTag(runBlocking { app.database.houses().observeAll().first() }.first { it.name == "Beach house" }.id))
    }

    @Test
    fun appStartOpensTheLastUsedHouseAboveTheList() {
        // seed() makes the first house the last used, then starts the app again.
        seed(houses = listOf(house("h2", "Parents"), house("h1", "Apartment")))

        tag(EMPTY_HINT_TAG).assertIsDisplayed()
        text("Parents").assertIsDisplayed()
        pressBack()
        tag(HOUSE_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun noBandOnTheHouseList() {
        seed(houses = listOf(house("h1", "Apartment"), house("h2", "Parents")))
        showHouseList()
        assertTrue(rule.onAllNodes(hasTestTag(HOUSE_BAND_TAG)).fetchSemanticsNodes().isEmpty())

        click(rowTag("h1"))
        tag(HOUSE_BAND_TAG)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun houseListScreenshot() {
        seed(
            houses = listOf(
                house("h1", "Apartment").copy(colorKey = "sky", address = "Rua Augusta, 1500, apto 42"),
                house("h2", "Parents' house").copy(colorKey = "coral", icon = "house"),
                house("h3", "Beach house").copy(colorKey = "mint"),
            ),
            containers = listOf(container("office", name = "Office")),
            items = listOf(item("laptop", containerId = "office", unitPrice = 350_000), item("lamp", unitPrice = 12_000), item("box", houseId = "h2")),
        )
        runBlocking { app.preferences.setCurrency("BRL") }
        showHouseList()
        rowShows("h1", "2 items")
        rule.onRoot().captureRoboImage("src/test/screenshots/HouseList_White.png")
    }

    /** Checks that a row's text contains [text]. */
    private fun rowShows(id: String, text: String) {
        rule.onNode(hasTestTag(rowTag(id)) and hasAnyDescendant(hasText(text, substring = true)), useUnmergedTree = true).assertExists()
    }

    /** Taps the node and waits for the UI to settle. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickAndIdle() {
        performClick()
        rule.waitForIdle()
    }
}
