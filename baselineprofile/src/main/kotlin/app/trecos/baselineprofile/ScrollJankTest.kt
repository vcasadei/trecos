package app.trecos.baselineprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The scrolling half of the performance pass (spec "Performance on low-end
 * phones", task 14.8): flings the Home list of 1,000 items and reads the share
 * of janky frames from the system's own counters (dumpsys gfxinfo).
 *
 * Macrobenchmark's traced frame metric misses an app that started before
 * tracing on Android 9-11, and its gfxinfo metric reads the counters after the
 * app has been stopped, so this test reads them itself while the app is open.
 * It writes the result in Macrobenchmark's JSON shape, as
 * `scroll-jank-benchmarkData.json` next to the benchmark results, for
 * scripts/check-benchmark.py.
 */
@RunWith(AndroidJUnit4::class)
class ScrollJankTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun scrollingOneThousandItems() {
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        device.executeShellCommand("am broadcast -n $TARGET_PACKAGE/.TestSeedReceiver --es fixture 1000-items")
        Thread.sleep(SEED_WAIT_MS)
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/.MainActivity")
        device.wait(Until.hasObject(By.text("Benchmark house")), WAIT_MS)
        device.waitForIdle()
        device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE reset")

        repeat(ROUNDS) { fling() }

        val gfxinfo = device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE")
        val total = Regex("""Total frames rendered: (\d+)""").find(gfxinfo)?.groupValues?.get(1)?.toInt() ?: 0
        val janky = Regex("""Janky frames: (\d+) \(([\d.]+)%\)""").find(gfxinfo)?.groupValues?.get(2)?.toDouble()
        assertTrue("dumpsys gfxinfo had no frames for $TARGET_PACKAGE:\n$gfxinfo", total > 0 && janky != null)
        writeResult(janky!!, total)
    }

    /** Flings the list down three times and back up three times. */
    private fun fling() {
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

    /**
     * Saves the result where the build pulls test output from.
     *
     * @param jankPercent the share of janky frames, in percent.
     * @param frames how many frames were rendered while scrolling.
     */
    private fun writeResult(jankPercent: Double, frames: Int) {
        val folder = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: instrumentation.targetContext.getExternalFilesDir(null)!!
        val json = """{"benchmarks": [{"name": "scrollingOneThousandItems", "metrics": {""" +
            """"gfxFrameJankPercent": {"median": $jankPercent}, "gfxFrameTotalCount": {"median": $frames}}}]}"""
        File(folder, "scroll-jank-benchmarkData.json").writeText(json)
    }

    private companion object {
        const val SEED_WAIT_MS = 8_000L
        const val WAIT_MS = 10_000L
        const val ROUNDS = 5
    }
}
