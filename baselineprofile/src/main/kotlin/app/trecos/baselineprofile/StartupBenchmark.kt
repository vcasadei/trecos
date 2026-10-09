package app.trecos.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The performance pass (spec "Performance on low-end phones", task 14.8):
 * cold start to the interactive Home screen over 10 runs with 1,000 items
 * stored, with and without the Baseline Profile, and scrolling those items.
 * `scripts/check-benchmark.py` gates a release on the results: a median of
 * 1.5 s or less, and janky frames under 5%.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Stores 1,000 items through the benchmark-only receiver. */
    @Before
    fun seed() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am broadcast -n $TARGET_PACKAGE/.BenchmarkSeedReceiver --ei count 1000")
        Thread.sleep(SEED_WAIT_MS)
    }

    @Test
    fun coldStartWithoutProfile() = coldStart(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() = coldStart(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun scrollingOneThousandItems() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        scrollTheList()
    }

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
    }

    private companion object {
        const val SEED_WAIT_MS = 8_000L
    }
}
