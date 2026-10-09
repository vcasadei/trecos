package app.trecos.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The application id of the app under test. */
const val TARGET_PACKAGE = "app.trecos"

private const val WAIT_MS = 5_000L

/**
 * Opens Search, Settings and Home in turn from the bottom bar, waiting for
 * each tab's label after the tap.
 */
fun MacrobenchmarkScope.visitEveryTab() {
    listOf("Search", "Settings", "Home").forEach { label ->
        device.wait(Until.findObject(By.text(label)), WAIT_MS)?.click()
        device.waitForIdle()
    }
}

/** Flings the Home list down and back up a few times. */
fun MacrobenchmarkScope.scrollTheList() {
    val x = device.displayWidth / 2
    val low = device.displayHeight * 3 / 4
    val high = device.displayHeight / 4
    repeat(3) {
        device.swipe(x, low, x, high, 8)
        device.waitForIdle()
    }
    repeat(3) {
        device.swipe(x, high, x, low, 8)
        device.waitForIdle()
    }
}
