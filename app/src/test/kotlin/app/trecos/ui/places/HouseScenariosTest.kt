package app.trecos.ui.places

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
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
        assertEquals(houses.first().id, runBlocking { app.preferences.lastHouseId.first() })
    }

    @Test
    fun creatingASecondHouse() {
        seed()
        text("Apartment").performClickAndIdle()
        click("switch_add_house")
        type("name", "Casa dos meus pais")
        type("address", "Rua das Flores, 10")
        saveAndClose()

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

        text("Apartment").assertIsDisplayed()
        text("Apartment").performClickAndIdle()
        click("switch_h2")

        text("Parents").assertIsDisplayed()
        assertEquals("h2", runBlocking { app.preferences.lastHouseId.first() })
    }

    /** Taps the node and waits for the UI to settle. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickAndIdle() {
        performClick()
        rule.waitForIdle()
    }
}
