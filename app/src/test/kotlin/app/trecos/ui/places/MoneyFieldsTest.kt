package app.trecos.ui.places

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Money fields in the forms show the display currency's symbol before the
 * value, so the user knows which currency they are typing.
 */
@RunWith(RobolectricTestRunner::class)
class MoneyFieldsTest : PlacesTestBase() {

    @Test
    fun unitPriceShowsTheCurrencySymbol() {
        runBlocking { app.preferences.setCurrency("BRL") }
        click("continue")
        click(ADD_BUTTON_TAG)
        text("Item").performClick()

        text("Unit price (R$)")
        type("price", "12")
        waitForTextIn("prefix_price", "R$")
    }

    @Test
    fun containerValueShowsTheCurrencySymbol() {
        runBlocking { app.preferences.setCurrency("EUR") }
        click("continue")
        click(ADD_BUTTON_TAG)
        text("Container").performClick()

        rule.onNode(hasTestTag(fieldTag("override")).and(hasText("(€)", substring = true))).assertExists()
        type("override", "50")
        waitForTextIn("prefix_override", "€")
    }
}
