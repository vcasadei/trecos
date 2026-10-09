package app.trecos

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Smoke test: the app starts with its container wired.
 */
@RunWith(RobolectricTestRunner::class)
class AppStartTest {

    @Test
    fun appStartsWithItsContainer() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = activity.application as TrecosApplication
                assertTrue(app is TestTrecosApplication)
                runBlocking { assertEquals(0, app.container.database.houses().count()) }
            }
        }
    }

    @Test
    fun preferencesHaveTheirDefaults() = runBlocking {
        val prefs = ApplicationProvider.getApplicationContext<TrecosApplication>().container.preferences
        assertEquals(app.trecos.data.ListView.Condensed, prefs.listView.first())
        assertEquals(app.trecos.data.HouseBand.Automatic, prefs.houseBand.first())
        assertEquals(null, prefs.lastHouseId.first())
    }
}
