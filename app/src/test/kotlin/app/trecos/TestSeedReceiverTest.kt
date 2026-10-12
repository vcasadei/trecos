package app.trecos

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import android.os.Looper

/**
 * The test-only seed receiver that device flows and the benchmark use to
 * start from known data (device-testing "Isolated and repeatable flows").
 */
@RunWith(RobolectricTestRunner::class)
class TestSeedReceiverTest {

    private val app = ApplicationProvider.getApplicationContext<TrecosApplication>()
    private val db get() = app.container.database

    @After
    fun restoreSwitch() {
        TestSeedReceiver.enabled = BuildConfig.TEST_SEED
    }

    @Test
    fun oneHouse() {
        seed("one-house")
        eventually { runBlocking { db.items().countActive() } == 4 }
        runBlocking {
            assertEquals(listOf("Apartment"), db.houses().observeAll().first().map { it.name })
            assertEquals(3, db.containers().observeAllInHouse("apartment").first().size)
            assertEquals("apartment", app.container.preferences.lastHouseId.first())
        }
    }

    @Test
    fun twoHouses() {
        seed("two-houses")
        eventually { runBlocking { db.items().countActive() } == 7 }
        runBlocking { assertEquals(setOf("Apartment", "Parents' house"), db.houses().observeAll().first().map { it.name }.toSet()) }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun photos() {
        seed("photos")
        eventually { runBlocking { db.photos().forOwner("laptop").size } == 3 }
        runBlocking { db.photos().forOwner("laptop").forEach { assertTrue(app.container.photoStore.photoFile(it.sha256).exists()) } }
    }

    @Test
    fun thousandItems() {
        seed("1000-items")
        eventually { runBlocking { db.items().countActive() } == 1000 }
        runBlocking { assertEquals("benchmark-house", app.container.preferences.lastHouseId.first()) }
    }

    @Test
    fun seedingTwiceStoresOnce() {
        seed("one-house")
        eventually { runBlocking { db.items().countActive() } == 4 }
        seed("one-house")
        Thread.sleep(500)
        runBlocking { assertEquals(4, db.items().countActive()) }
    }

    @Test
    fun unknownFixtureStoresNothing() {
        seed("everything")
        Thread.sleep(500)
        runBlocking { assertEquals(0, db.houses().count()) }
    }

    /** Scenario "The seeding hook in a release build". */
    @Test
    fun releaseBuildStoresNothing() {
        TestSeedReceiver.enabled = false
        seed("one-house")
        Thread.sleep(500)
        runBlocking { assertEquals(0, db.houses().count()) }
    }

    private fun seed(fixture: String) {
        app.sendBroadcast(Intent().setClassName(app.packageName, TestSeedReceiver::class.java.name).putExtra("fixture", fixture))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun eventually(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Condition not met in $timeoutMs ms" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
    }
}
