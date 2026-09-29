package app.trecos.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Transition durations. Every transition lasts 150-250 ms, and all of them
 * become instant when Android's "Remove animations" setting is on.
 *
 * @property shortMs quick feedback such as fades, in milliseconds.
 * @property mediumMs movements such as the bottom bar circle, in milliseconds.
 * @property longMs larger changes such as opening a photo, in milliseconds.
 */
@Immutable
data class Motion(val shortMs: Int, val mediumMs: Int, val longMs: Int) {

    /** Whether transitions animate at all. */
    val enabled: Boolean get() = longMs > 0

    /**
     * Builds an animation spec lasting [durationMs], or a snap when motion is off.
     *
     * @param durationMs one of this motion's durations.
     * @return a tween of that length, or an instant snap.
     */
    fun <T> spec(durationMs: Int): FiniteAnimationSpec<T> =
        if (enabled) tween(durationMs) else snap()

    companion object {
        /** The standard durations. */
        val Standard = Motion(shortMs = 150, mediumMs = 200, longMs = 250)

        /** No animation, for "Remove animations". */
        val Instant = Motion(shortMs = 0, mediumMs = 0, longMs = 0)

        /**
         * Chooses the motion for the system animator duration scale.
         *
         * @param animatorScale `Settings.Global.ANIMATOR_DURATION_SCALE`; 0 means "Remove animations".
         * @return [Instant] when the scale is 0, otherwise [Standard].
         */
        fun forAnimatorScale(animatorScale: Float): Motion =
            if (animatorScale == 0f) Instant else Standard

        /**
         * Reads the system animator duration scale and chooses the motion for it.
         *
         * @param context any context of the app.
         * @return the motion matching the phone's animation setting.
         */
        fun fromSystem(context: Context): Motion = forAnimatorScale(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
}

/** The motion in effect, provided by [TrecosTheme]. */
val LocalMotion = staticCompositionLocalOf { Motion.Standard }

/**
 * Remembers the motion matching the phone's animation setting.
 *
 * @return [Motion.Instant] when "Remove animations" is on, otherwise [Motion.Standard].
 */
@Composable
fun rememberSystemMotion(): Motion {
    val context = LocalContext.current
    return remember(context) { Motion.fromSystem(context) }
}
