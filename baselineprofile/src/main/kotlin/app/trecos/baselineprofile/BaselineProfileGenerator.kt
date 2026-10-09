package app.trecos.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile from the paths every user takes: cold
 * start with 1,000 items, scrolling them, and switching between the three tabs. Run it with
 * `./gradlew :baselineprofile:generateBaselineProfile` on a connected
 * emulator or rooted device.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE) {
        device.executeShellCommand("am broadcast -n $TARGET_PACKAGE/.BenchmarkSeedReceiver --ei count 1000")
        Thread.sleep(8_000)
        pressHome()
        startActivityAndWait()
        scrollTheList()
        visitEveryTab()
    }
}
