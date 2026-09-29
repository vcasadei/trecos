package app.trecos.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold start to the Home screen over 10 runs, with and without the
 * Baseline Profile. The release gate (median of 1.5 s or less on the 2 GB
 * Android 9 reference phone) is checked with these numbers.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

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
    }
}
