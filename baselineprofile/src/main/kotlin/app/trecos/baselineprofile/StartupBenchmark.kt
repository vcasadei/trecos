package app.trecos.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The performance pass (spec "Performance on low-end phones", task 14.8):
 * cold start to the interactive Home screen over 10 runs with 1,000 items
 * stored, with and without the Baseline Profile. Scrolling is measured by
 * [ScrollJankTest].
 * `scripts/check-benchmark.py` gates a release on the results: a median of
 * 1.5 s or less, and janky frames under 5%.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Stores 1,000 items through the test-only seed receiver. */
    @Before
    fun seed() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am broadcast -n $TARGET_PACKAGE/.TestSeedReceiver --es fixture 1000-items")
        Thread.sleep(SEED_WAIT_MS)
    }

    @Test
    fun coldStartWithoutProfile() = coldStart(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() = coldStart(CompilationMode.Partial(BaselineProfileMode.Require))

    /**
     * Cold-starts the app 10 times with the given compilation.
     *
     * @param compilationMode how the app is compiled before measuring.
     */
    private fun coldStart(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        // startActivityAndWait() returns at the first frame. The list reports fully
        // drawn once it has loaded, and the trace must still be running then, with
        // the frame after it, or there is no time to full display on a slow emulator.
        device.wait(Until.hasObject(By.res(PLACE_LIST_TAG)), WAIT_MS)
        device.waitForIdle()
    }

    private companion object {
        const val SEED_WAIT_MS = 8_000L
        const val WAIT_MS = 10_000L
        const val PLACE_LIST_TAG = "place_list"
    }
}
