package app.trecos.ui.help

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.trecos.data.Fixtures.item
import app.trecos.help.Contact
import app.trecos.help.Faq
import app.trecos.help.ReviewPrompter
import app.trecos.help.RatingPolicy
import app.trecos.help.Tip
import app.trecos.help.TipJar
import app.trecos.help.TipOutcome
import app.trecos.ui.places.PlacesTestBase
import app.trecos.ui.settings.SETTINGS_LIST_TAG
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.tabTag
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Scenarios of the help-and-support spec (tasks 14.1-14.5). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class HelpScenariosTest : PlacesTestBase() {

    /** Plays a scripted tip jar. */
    private class FakeTips : TipJar {
        var available = true
        var outcome = TipOutcome.Thanked
        val bought = mutableListOf<String>()

        override suspend fun tips(context: Context): List<Tip>? =
            if (available) listOf(Tip("tip_small", "R$ 5,00"), Tip("tip_medium", "R$ 10,00"), Tip("tip_large", "R$ 25,00")) else null

        override suspend fun buy(activity: Activity, id: String): TipOutcome {
            bought += id
            return outcome
        }
    }

    private val tips = FakeTips()
    private var reviews = 0

    @Before
    fun useFakes() {
        app.tips = tips
        app.review = ReviewPrompter { reviews++ }
    }

    /** @return whether a node with the tag exists. */
    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Opens Settings and taps a row.
     *
     * @param key the row's key.
     */
    private fun settingsRow(key: String) {
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_$key"))
        // A row scrolled to the bottom edge sits under the floating bottom bar, so tap it through its click action.
        tag("setting_$key").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    /** Waits for text anywhere. */
    private fun shows(text: String) =
        rule.waitUntil(10_000) { rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun readingAnAnswer() {
        seed()
        settingsRow("faq")
        tag("faq_list").performScrollToNode(hasTestTag("faq_9"))
        tag("faq_9")
        rule.onNodeWithTag("faq_9").assertTextContains("Do I need an account?")
        click("faq_9")

        tag("faq_answer_9").assertTextContains("works without any account", substring = true)
        (1..10).filter { it != 9 }.forEach { assertFalse("answer $it stays collapsed", exists("faq_answer_$it")) }

        tag("faq_list").performScrollToNode(hasTestTag("faq_2"))
        click("faq_2")
        tag("faq_answer_2")
        assertFalse(exists("faq_answer_9"))
    }

    @Test
    fun theFaqHasTenQuestionsInBothLanguages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("en", "pt-BR").forEach { language ->
            val entries = Faq.parse(context.assets.open("faq/$language.md").bufferedReader().readText())
            assertEquals(language, (1..10).toList(), entries.map { it.number })
            assertTrue(entries.all { it.answer.isNotBlank() && !it.answer.contains("](") })
        }
    }

    @Test
    fun askingForAQuote() {
        seed()
        val mail = android.content.ComponentName("com.example.mail", "com.example.mail.Compose")
        shadowOf(rule.activity.packageManager).apply {
            addActivityIfNotPresent(mail)
            addIntentFilterForActivity(mail, android.content.IntentFilter(Intent.ACTION_SENDTO).apply { addCategory(Intent.CATEGORY_DEFAULT); addDataScheme("mailto") })
        }
        settingsRow("contact")
        click("subject_LicenseQuote")

        val sent = shadowOf(rule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, sent.action)
        assertEquals("mailto:hello@trecos.app", sent.data.toString())
        assertEquals("License quote", sent.getStringExtra(Intent.EXTRA_SUBJECT))
        val body = sent.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(body.contains("Trecos ") && body.contains("Android "))
        assertFalse("no other data is added", body.contains("Apartment"))
    }

    @Test
    fun noEmailApp() {
        seed()
        settingsRow("contact")
        click("subject_Question")

        tag("contact_address").assertTextContains("hello@trecos.app")
        click("copy_address")
        val clipboard = rule.activity.getSystemService(ClipboardManager::class.java)
        assertEquals("hello@trecos.app", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    /** Sets the rating counters and returns to Home, where the prompt may show. */
    private fun returnHomeAfter(days: Int, items: Int, sessions: Int) {
        seed(items = (1..items).map { item("i$it") })
        runBlocking { app.preferences.setRatingCounters(app.clock() - days * RatingPolicy.DAY, sessions) }
        click(tabTag(TrecosTab.Settings))
        click(tabTag(TrecosTab.Home))
        rule.waitForIdle()
    }

    @Test
    fun theSinglePrompt() {
        returnHomeAfter(days = 15, items = 20, sessions = 5)
        rule.waitUntil(10_000) { reviews == 1 }

        click(tabTag(TrecosTab.Settings))
        click(tabTag(TrecosTab.Home))
        rule.waitForIdle()
        assertEquals("never again afterwards", 1, reviews)
    }

    @Test
    fun tooEarly() {
        returnHomeAfter(days = 3, items = 50, sessions = 9)
        assertEquals(0, reviews)
    }

    @Test
    fun theRules() {
        val day = RatingPolicy.DAY
        assertTrue(RatingPolicy.shouldPrompt(0, 14 * day, 20, 5, alreadyShown = false, inFlow = false))
        assertFalse(RatingPolicy.shouldPrompt(0, 14 * day, 19, 5, alreadyShown = false, inFlow = false))
        assertFalse(RatingPolicy.shouldPrompt(0, 14 * day, 20, 4, alreadyShown = false, inFlow = false))
        assertFalse(RatingPolicy.shouldPrompt(0, 14 * day, 20, 5, alreadyShown = true, inFlow = false))
        assertFalse(RatingPolicy.shouldPrompt(0, 14 * day, 20, 5, alreadyShown = false, inFlow = true))
        assertFalse(RatingPolicy.shouldPrompt(0, 13 * day, 99, 99, alreadyShown = false, inFlow = false))
    }

    @Test
    fun tipping() {
        seed()
        settingsRow("tips")
        click("tip_medium")

        shows("Thank you for supporting Trecos!")
        assertEquals(listOf("tip_medium"), tips.bought)
    }

    @Test
    fun purchaseCancelled() {
        seed()
        tips.outcome = TipOutcome.Cancelled
        settingsRow("tips")
        click("tip_small")

        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasText("Thank you", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun googlePlayUnavailable() {
        seed()
        tips.available = false
        settingsRow("tips")

        tag("tips_unavailable").assertTextContains("Tips need Google Play", substring = true)
    }

    @Test
    fun aboutShowsTheLicenseAndLinks() {
        seed()
        settingsRow("about")
        tag("about_license").assertTextContains("PolyForm Noncommercial License 1.0.0", substring = true)
        tag("about_license").assertTextContains("Commercial use requires a license", substring = true)
        listOf("about_source", "about_website", "about_privacy", "about_contact", "about_rate", "about_licenses").forEach { tag(it) }
    }

    @Test
    fun viewingLicenses() {
        seed()
        settingsRow("about")
        click("about_licenses")
        tag("licenses")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = context.resources.getIdentifier("aboutlibraries", "raw", context.packageName)
        val json = Json.parseToJsonElement(context.resources.openRawResource(id).bufferedReader().readText()).jsonObject
        val names = json.getValue("libraries").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }.distinct()
        assertTrue("the shipped libraries are there", names.size > 50)
        listOf("sqlcipher", "ZXing", "Room").forEach { wanted ->
            assertTrue("$wanted is listed", names.any { it.contains(wanted, ignoreCase = true) })
        }
        val shown = parseLibraries(context.resources.openRawResource(id).bufferedReader().readText())
        val ids = json.getValue("libraries").jsonArray.map { it.jsonObject.getValue("uniqueId").jsonPrimitive.content }
        assertEquals("every bundled library is listed", ids.toSet(), shown.map { it.id }.toSet())
        shown.forEach { library -> tag("licenses").performScrollToNode(hasTestTag("library_${library.id}")) }

        val sqlcipher = shown.first { it.name.contains("SQLCipher", ignoreCase = true) }
        tag("licenses").performScrollToNode(hasTestTag("library_${sqlcipher.id}"))
        click("library_${sqlcipher.id}")
        tag("license_text")
        assertTrue(sqlcipher.text.isNotBlank())
    }
}
