package app.trecos.help

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

/** When the single automatic rating prompt may show (spec "Rating", design D18). */
object RatingPolicy {
    /** How long after installing. */
    const val MIN_DAYS = 14

    /** How many items the user must have. */
    const val MIN_ITEMS = 20

    /** How many separate sessions. */
    const val MIN_SESSIONS = 5

    /**
     * @param installedAt when the app was first opened.
     * @param now the current time.
     * @param items how many items there are (not counting the trash).
     * @param sessions how many separate sessions there were.
     * @param alreadyShown whether the prompt was ever shown.
     * @param inFlow whether the user is adding, editing, deleting or resolving conflicts.
     * @return whether to show the prompt now.
     */
    fun shouldPrompt(installedAt: Long, now: Long, items: Int, sessions: Int, alreadyShown: Boolean, inFlow: Boolean): Boolean =
        !alreadyShown && !inFlow && items >= MIN_ITEMS && sessions >= MIN_SESSIONS && now - installedAt >= MIN_DAYS * DAY

    /** A day in milliseconds. */
    const val DAY = 24L * 60 * 60 * 1000

    /** A gap long enough to count as a new session. */
    const val SESSION_GAP = 30L * 60 * 1000
}

/** Shows Google Play's in-app review; tests use a fake. */
fun interface ReviewPrompter {
    /**
     * Asks Play to show the rating card (Play decides whether it appears).
     *
     * @param activity the current activity.
     */
    fun show(activity: Activity)
}

/** Google Play's In-App Review API. */
object PlayReviewPrompter : ReviewPrompter {
    override fun show(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnSuccessListener { info -> manager.launchReviewFlow(activity, info) }
    }
}

/** Opens Trecos's Google Play page, in the Play app if it is there. */
object PlayListing {
    /**
     * @param context a context.
     */
    fun open(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
    }
}
