package app.trecos.ui.places

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import app.trecos.R
import com.github.takahirom.roborazzi.captureRoboImage
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.item
import app.trecos.ui.language.AppLanguage
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
 * Scenarios of the items "Item fields", "Total value", "Dates added and
 * changed", "Add and edit form" and "Item screen" requirements.
 */
@RunWith(RobolectricTestRunner::class)
class ItemScenariosTest : PlacesTestBase() {

    private val boxA = container("boxA", name = "Box A")

    /** Opens the new-item form on the house's top level. */
    private fun newItem() {
        click(ADD_BUTTON_TAG)
        text("Item").performClick()
        rule.waitForIdle()
    }

    /** @return the items on the house's top level. */
    private fun topLevelItems() = runBlocking { app.database.items().observeIn("h1", null).first() }

    @Test
    fun minimalItem() {
        seed()
        newItem()
        type("name", "Raspberry Pi 4")
        click("save")

        text("Raspberry Pi 4").assertIsDisplayed()
        val saved = topLevelItems().single()
        assertEquals(1, saved.quantity)
        assertNull(saved.unitPrice)
    }

    @Test
    fun invalidQuantity() {
        seed()
        newItem()
        type("name", "Screws")
        listOf("2.5", "-1").forEach { bad ->
            type("quantity", bad)
            click("save")
            tag("error_quantity").assertTextContains("whole number", substring = true)
        }
        assertTrue(topLevelItems().isEmpty())
    }

    @Test
    fun missingName() {
        seed()
        newItem()
        click("save")

        tag("error_name").assertIsDisplayed()
        assertTrue(topLevelItems().isEmpty())
    }

    @Test
    fun screws() {
        seed(items = listOf(item("screws", quantity = 200, unitPrice = 10).copy(name = "M3 screws")))
        runBlocking { app.preferences.setCurrency("BRL") }
        click(rowTag("screws"))

        detail(R.string.total_value).assertTextContains("R$20.00", substring = true)
    }

    @Test
    fun editingUpdatesTheDate() {
        val newYear = 1_767_225_600_000L
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi", createdAt = newYear, updatedAt = newYear)))
        click(rowTag("pi"))
        detail(R.string.date_changed).assertTextContains(formatDate(newYear, AppLanguage.English), substring = true)

        rule.onNode(androidx.compose.ui.test.hasContentDescription("Edit")).performClick()
        type("quantity", "5")
        click("save")

        val today = formatDate(System.currentTimeMillis(), AppLanguage.English)
        detail(R.string.date_changed).assertTextContains(today, substring = true)
        detail(R.string.date_added).assertTextContains(formatDate(newYear, AppLanguage.English), substring = true)
        val saved = runBlocking { app.database.items().get("pi")!! }
        assertEquals(5, saved.quantity)
        assertEquals(newYear, saved.createdAt)
        assertTrue(saved.updatedAt > newYear)
    }

    @Test
    fun saveAndNewKeepsTheContainer() {
        seed(containers = listOf(boxA))
        click(rowTag("boxA"))
        newItem()
        type("name", "USB-C cable")
        click("save_new")

        tag(fieldTag("name")).assertTextContains("")
        type("name", "HDMI cable")
        click("save")

        text("HDMI cable").assertIsDisplayed()
        val saved = runBlocking { app.database.items().observeIn("h1", "boxA").first() }
        assertEquals(listOf("HDMI cable", "USB-C cable"), saved.map { it.name })
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun priceFormattingInPortuguese() {
        seed(items = listOf(item("pi", unitPrice = 123_450).copy(name = "Raspberry Pi")))
        runBlocking { app.preferences.setCurrency("BRL") }
        click(rowTag("pi"))

        detail(R.string.field_unit_price).assertTextContains("R$ 1.234,50", substring = true)
    }

    @Test
    fun priceFormattingInEnglish() {
        seed(items = listOf(item("pi", unitPrice = 123_450).copy(name = "Raspberry Pi")))
        runBlocking { app.preferences.setCurrency("BRL") }
        click(rowTag("pi"))

        detail(R.string.field_unit_price).assertTextContains("R$1,234.50", substring = true)
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun itemScreenScreenshot() {
        seed(
            containers = listOf(boxA),
            items = listOf(
                item("pi", containerId = "boxA", quantity = 2, unitPrice = 45_000).copy(
                    name = "Raspberry Pi 4 Model B", brand = "Raspberry Pi", model = "4B 4GB",
                    serial = "SN4C8E2F9A7B1D3E5F7A9C2E4B6D8F0A1C3E5G7K", description = "Spare boards for the home lab.",
                ),
            ),
        )
        runBlocking { app.preferences.setCurrency("BRL") }
        click(rowTag("boxA"))
        click(rowTag("pi"))
        detail(R.string.total_value)
        rule.onRoot().captureRoboImage("src/test/screenshots/ItemScreen_White.png")
    }

    @Test
    fun emptyFieldsAreHiddenOnTheItemScreen() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        click(rowTag("pi"))
        detail(R.string.field_quantity)

        listOf(R.string.field_unit_price, R.string.total_value, R.string.field_brand, R.string.field_serial).forEach { label ->
            assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasTestTag(detailTag(label))).fetchSemanticsNodes().isEmpty())
        }
    }
}
