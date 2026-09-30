package app.trecos.ui.settings

import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.trecos.data.AppPreferences
import app.trecos.data.Fixtures.item
import app.trecos.data.ListView
import app.trecos.data.StartScreen
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.places.PlacesTestBase
import app.trecos.ui.places.rowTag
import app.trecos.ui.shell.TrecosApp
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.rootScreenTag
import app.trecos.ui.shell.tabTag
import app.trecos.ui.theme.ThemeMode
import app.trecos.ui.theme.TrecosTheme
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenarios of the settings spec (tasks 9.7-9.9): structure and defaults,
 * immediate effect, the currency warning, and the start screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class SettingsScenariosTest : PlacesTestBase() {

    /**
     * Scrolls the Settings list to a row.
     *
     * @param key the row's key.
     */
    private fun showRow(key: String) {
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_$key"))
    }

    /** Opens the Settings tab. */
    private fun openSettings() = click(tabTag(TrecosTab.Settings))

    @Test
    fun freshInstall() {
        val previous = Locale.getDefault()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"))
            val dir = File.createTempFile("prefs", "").apply { delete(); mkdirs() }
            val prefs = AppPreferences(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
            runBlocking {
                assertEquals("BRL", prefs.currency.first())
                assertEquals(StartScreen.Home, prefs.startScreen.first())
                assertEquals(ThemeMode.FollowSystem, prefs.theme.first())
            }
            assertEquals(AppLanguage.PortugueseBrazil, AppLanguage.resolve(listOf(Locale.forLanguageTag("pt-BR"))))
        } finally {
            Locale.setDefault(previous)
            scope.cancel()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h2400dp-xhdpi")
    fun sectionsAndDefaultsOnScreen() {
        seed()
        openSettings()
        waitForTextIn("value_language", "English")
        waitForTextIn("value_start", "Home")
        waitForTextIn("value_theme", "Follow system")
        showRow("version")
        val tops = listOf("General", "Appearance", "Items & photos", "Custom fields", "Trash", "About").map { title ->
            rule.onAllNodes(androidx.compose.ui.test.hasText(title), useUnmergedTree = true).fetchSemanticsNodes().minOf { it.boundsInRoot.top }
        }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun immediateEffect() {
        seed()
        openSettings()
        click("setting_theme")
        click("option_theme_${ThemeMode.Dark}")

        rule.waitUntil(10_000) { runBlocking { app.preferences.theme.first() } == ThemeMode.Dark }
        rule.waitForIdle()
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(android.graphics.Color.BLACK, image.getPixel(image.width / 2, image.height / 3))
    }

    @Test
    fun relabelling() {
        seed(items = listOf(item("tv", unitPrice = 350_000).copy(name = "TV")))
        runBlocking {
            app.preferences.setCurrency("BRL")
            app.preferences.setListView(ListView.Detailed)
        }
        openSettings()
        click("setting_currency")
        type("currency_search_raw", "USD")
        click("currency_USD")
        tag("currency_warning")
        click("confirm_currency")

        rule.waitUntil(10_000) { runBlocking { app.preferences.currency.first() } == "USD" }
        click(tabTag(TrecosTab.Home))
        rule.onNode(hasTestTag(rowTag("tv"))).assertTextContains("$3,500.00", substring = true)
    }

    @Test
    fun cancelling() {
        seed()
        runBlocking { app.preferences.setCurrency("BRL") }
        openSettings()
        click("setting_currency")
        type("currency_search_raw", "USD")
        click("currency_USD")
        click("cancel_currency")

        assertEquals("BRL", runBlocking { app.preferences.currency.first() })
        tag("currency_search")
    }

    @Test
    fun startOnSearch() {
        seed()
        openSettings()
        click("setting_start")
        click("option_start_${StartScreen.Search}")
        rule.waitUntil(10_000) { runBlocking { app.preferences.startScreen.first() } == StartScreen.Search }

        rule.runOnUiThread { rule.activity.setContent { TrecosTheme { TrecosApp() } } }
        tag(rootScreenTag(TrecosTab.Search))
        assertTrue(rule.onAllNodes(hasTestTag(rootScreenTag(TrecosTab.Home))).fetchSemanticsNodes().isEmpty())
    }
}
