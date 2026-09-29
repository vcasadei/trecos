package app.trecos.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks the motion tokens and that "Remove animations" makes transitions instant.
 */
@RunWith(RobolectricTestRunner::class)
class MotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standardDurationsStayBetween150And250Ms() {
        with(Motion.Standard) {
            listOf(shortMs, mediumMs, longMs).forEach { assertTrue("$it ms", it in 150..250) }
        }
    }

    @Test
    fun animatorScaleZeroMeansInstant() {
        assertEquals(Motion.Instant, Motion.forAnimatorScale(0f))
        assertEquals(Motion.Standard, Motion.forAnimatorScale(1f))
        assertEquals(Motion.Standard, Motion.forAnimatorScale(0.5f))
    }

    @Test
    fun transitionsAreInstantWhenAnimatorScaleIsZero() {
        setAnimatorScale(0f)
        assertEquals(1f, valueFourFramesAfterChange(), 0f)
    }

    @Test
    fun transitionsAnimateWithTheDefaultScale() {
        setAnimatorScale(1f)
        val value = valueFourFramesAfterChange()
        assertTrue("value $value", value > 0f && value < 1f)
    }

    /**
     * Sets the system animator duration scale.
     *
     * @param scale the value for `ANIMATOR_DURATION_SCALE`.
     */
    private fun setAnimatorScale(scale: Float) {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    /**
     * Animates a value from 0 to 1 with the theme's medium duration and reads
     * it four frames (64 ms) after the change, well inside the 200 ms tween.
     *
     * @return the animated value four frames later.
     */
    private fun valueFourFramesAfterChange(): Float {
        val target = mutableStateOf(0f)
        var value = -1f
        composeRule.setContent {
            TrecosTheme {
                val motion = LocalMotion.current
                val animated by animateFloatAsState(target.value, motion.spec(motion.mediumMs), label = "test")
                value = animated
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            target.value = 1f
            Snapshot.sendApplyNotifications()
        }
        repeat(4) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        return value
    }
}
