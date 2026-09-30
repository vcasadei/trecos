package app.trecos.ui.places

import android.os.Looper
import app.trecos.data.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * "Save + new" resets the form after the save finishes; what the user types
 * in the meantime must survive (a race first seen on CI).
 */
@RunWith(RobolectricTestRunner::class)
class SaveAndNewRaceTest : PlacesTestBase() {
    // The activity rule of the base class supplies the app; the form model is driven directly.

    /** Runs the main thread until the save coroutine is done. */
    private fun settle() {
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun typingDuringTheSaveIsKept() {
        runBlocking { app.database.houses().insert(Fixtures.house()) }
        val vm = ItemFormViewModel(app, "h1", null, null)
        settle()
        vm.name = "cabo usb-c 1m"
        vm.save(andNew = true) {}
        // The save is still running: the user already types the next item.
        vm.name = "cabo usb-c 2m"
        vm.quantity = "3"
        settle()

        assertEquals("cabo usb-c 2m", vm.name)
        assertEquals("3", vm.quantity)
        assertEquals(listOf("cabo usb-c 1m"), runBlocking { app.database.items().observeIn("h1", null).first() }.map { it.name })
    }

    @Test
    fun untouchedFieldsAreCleared() {
        runBlocking { app.database.houses().insert(Fixtures.house()) }
        val vm = ItemFormViewModel(app, "h1", null, null)
        settle()
        vm.name = "Mouse"
        vm.brand = "Logitech"
        vm.save(andNew = true) {}
        settle()

        assertEquals("", vm.name)
        assertEquals("", vm.brand)
        assertEquals("1", vm.quantity)
    }
}
