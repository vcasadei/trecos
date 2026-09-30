package app.trecos.help

import android.content.Intent
import android.net.Uri

/** Why someone writes to the author (spec "Contact"). */
enum class ContactSubject { Question, LicenseQuote, Suggestion, Problem }

/** Builds the "Contact us" e-mail: the address, a subject type and version details only. */
object Contact {
    /** The contact address. */
    const val ADDRESS = "hello@trecos.app"

    /**
     * @param subject the subject text, in the app language.
     * @param appVersion the app version.
     * @param androidVersion the Android version.
     * @return an intent for the user's e-mail app.
     */
    fun intent(subject: String, appVersion: String, androidVersion: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$ADDRESS")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "\n\n—\nTrecos $appVersion\nAndroid $androidVersion")
        }
}
