package app.trecos.ui.help

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.trecos.AppMessage
import app.trecos.R
import app.trecos.help.Contact
import app.trecos.help.ContactSubject
import app.trecos.help.Faq
import app.trecos.help.FaqEntry
import app.trecos.help.PlayListing
import app.trecos.help.Tip
import app.trecos.help.TipOutcome
import app.trecos.ui.appContainer
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.places.BottomBarClearance
import app.trecos.ui.shell.TrecosTopBar
import app.trecos.ui.text.SafeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch

/** The source code. */
const val SOURCE_URL = "https://github.com/vcasadei/trecos"

/** The website. */
const val WEBSITE_URL = "https://trecos.app"

/** The privacy policy; on GitHub until the website hosts it (task 14.9). */
const val PRIVACY_URL = "https://github.com/vcasadei/trecos/blob/master/PRIVACY.md"

/**
 * @param context a context.
 * @return the FAQ in the app language, from the bundled `faq/<language>.md`.
 */
fun loadFaq(context: Context): List<FaqEntry> {
    val tag = AppLanguage.current().tag
    val text = runCatching { context.assets.open("faq/$tag.md") }.recoverCatching { context.assets.open("faq/en.md") }.getOrNull()
        ?.bufferedReader()?.use { it.readText() }.orEmpty()
    return Faq.parse(text)
}

/**
 * The FAQ as an accordion (spec "FAQ"): tapping a question opens its answer
 * and closes any other.
 *
 * @param onBack leaves the screen.
 */
@Composable
fun FaqScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries = remember { loadFaq(context) }
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.faq_title), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = BottomBarClearance), modifier = Modifier.testTag("faq_list")) {
            items(entries, key = { it.number }) { entry ->
                val expanded = open == entry.number
                Column(Modifier.fillMaxWidth().animateContentSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { open = if (expanded) null else entry.number }
                            .semantics {
                                role = Role.Button
                                stateDescription = if (expanded) "expanded" else "collapsed"
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("faq_${entry.number}"),
                    ) {
                        SafeText(entry.question, maxLines = 3, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                    if (expanded) {
                        Text(
                            bold(entry.answer),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).testTag("faq_answer_${entry.number}"),
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** @return the text with `**bold**` spans applied. */
private fun bold(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part) }
}

/**
 * "Contact us" (spec "Contact"): choose a subject type, then the e-mail app
 * opens addressed to hello@trecos.app with the version details; without an
 * e-mail app, the address is shown with a copy button.
 *
 * @return starts contacting.
 */
@Composable
fun rememberContact(): () -> Unit {
    val context = LocalContext.current
    val app = appContainer()
    var choosing by remember { mutableStateOf(false) }
    var noApp by remember { mutableStateOf(false) }
    val subjects = listOf(
        ContactSubject.Question to stringResource(R.string.subject_question),
        ContactSubject.LicenseQuote to stringResource(R.string.subject_quote),
        ContactSubject.Suggestion to stringResource(R.string.subject_suggestion),
        ContactSubject.Problem to stringResource(R.string.subject_problem),
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.contact_title)) },
            text = {
                Column {
                    subjects.forEach { (subject, label) ->
                        Text(
                            label,
                            modifier = Modifier.fillMaxWidth().clickable {
                                choosing = false
                                val intent = Contact.intent(label, versionName(context), Build.VERSION.RELEASE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent) else noApp = true
                            }.padding(vertical = 14.dp).testTag("subject_$subject"),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (noApp) {
        AlertDialog(
            onDismissRequest = { noApp = false },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.contact_no_app))
                    Text(Contact.ADDRESS, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("contact_address"))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("e-mail", Contact.ADDRESS))
                    app.messages.tryEmit(AppMessage(app.resources.getString(R.string.copied)))
                    noApp = false
                }, modifier = Modifier.testTag("copy_address")) { Text(stringResource(R.string.action_copy)) }
            },
            dismissButton = { TextButton(onClick = { noApp = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    return { choosing = true }
}

/** @return the app version. */
private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

/**
 * Settings > Support Trecos > Leave a tip (spec "Tips").
 *
 * @param onBack leaves the screen.
 */
@Composable
fun TipsScreen(onBack: () -> Unit) {
    val app = appContainer()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val tips by produceState<List<Tip>?>(initialValue = emptyList()) { value = app.tips.tips(context) }
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.tips_title), onBack = onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tips_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val available = tips
            if (available == null) {
                Text(stringResource(R.string.tips_unavailable), modifier = Modifier.testTag("tips_unavailable"))
            } else {
                available.forEach { tip ->
                    val label = stringResource(
                        when (tip.id) {
                            "tip_small" -> R.string.tip_small
                            "tip_medium" -> R.string.tip_medium
                            else -> R.string.tip_large
                        },
                    )
                    Button(onClick = {
                        val host = activity ?: return@Button
                        scope.launch {
                            when (app.tips.buy(host, tip.id)) {
                                TipOutcome.Thanked -> app.messages.tryEmit(AppMessage(app.resources.getString(R.string.tips_thanks)))
                                TipOutcome.Failed -> app.messages.tryEmit(AppMessage(app.resources.getString(R.string.tips_failed)))
                                TipOutcome.Cancelled -> Unit
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag(tip.id)) { Text("$label · ${tip.price}") }
                }
            }
        }
    }
}

/**
 * Settings > About (spec "About").
 *
 * @param onLicenses opens the open-source licenses.
 * @param onBack leaves the screen.
 */
@Composable
fun AboutScreen(onLicenses: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val contact = rememberContact()
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.about_title), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, BottomBarClearance), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Trecos ${versionName(context)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("about_version")) }
            item { Text(stringResource(R.string.about_license), modifier = Modifier.testTag("about_license")) }
            item { OutlinedButton(onClick = { open(SOURCE_URL) }, modifier = Modifier.testTag("about_source")) { Text(stringResource(R.string.about_source)) } }
            item { OutlinedButton(onClick = { open(WEBSITE_URL) }, modifier = Modifier.testTag("about_website")) { Text(stringResource(R.string.about_website)) } }
            item { OutlinedButton(onClick = { open(PRIVACY_URL) }, modifier = Modifier.testTag("about_privacy")) { Text(stringResource(R.string.about_privacy)) } }
            item { OutlinedButton(onClick = contact, modifier = Modifier.testTag("about_contact")) { Text(stringResource(R.string.setting_contact)) } }
            item { OutlinedButton(onClick = { PlayListing.open(context) }, modifier = Modifier.testTag("about_rate")) { Text(stringResource(R.string.setting_rate)) } }
            item { Button(onClick = onLicenses, modifier = Modifier.testTag("about_licenses")) { Text(stringResource(R.string.about_licenses)) } }
        }
    }
}

/**
 * One shipped library.
 *
 * @property id AboutLibraries' unique id (group and artifact).
 * @property name its name.
 * @property version its version.
 * @property licenses its licenses' names.
 * @property text the licenses' texts.
 */
data class LicensedLibrary(val id: String, val name: String, val version: String, val licenses: List<String>, val text: String)

/**
 * Reads the list AboutLibraries generates from the Gradle metadata (design D18).
 *
 * @param json the generated `aboutlibraries.json`.
 * @return every library, by name.
 */
fun parseLibraries(json: String): List<LicensedLibrary> {
    val root = Json.parseToJsonElement(json).jsonObject
    val licenses = root["licenses"]?.jsonObject.orEmpty()
    return root["libraries"]?.jsonArray.orEmpty().map { it.jsonObject }.map { lib ->
        val ids = lib["licenses"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        LicensedLibrary(
            id = lib["uniqueId"]?.jsonPrimitive?.content.orEmpty(),
            name = lib["name"]?.jsonPrimitive?.content ?: lib["uniqueId"]?.jsonPrimitive?.content.orEmpty(),
            version = lib["artifactVersion"]?.jsonPrimitive?.content.orEmpty(),
            licenses = ids.map { id -> licenses[id]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: id },
            text = ids.joinToString("\n\n") { id ->
                val license = licenses[id]?.jsonObject
                license?.get("content")?.jsonPrimitive?.content ?: license?.get("url")?.jsonPrimitive?.content ?: id
            },
        )
    }.sortedBy { it.name.lowercase() }
}

/**
 * Every third-party component shipped in the app with its license text,
 * from the list AboutLibraries generates at build time (design D18).
 *
 * @param onBack leaves the screen.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val libraries = remember { parseLibraries(context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }) }
    var shown by remember { mutableStateOf<LicensedLibrary?>(null) }
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.about_licenses), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = BottomBarClearance), modifier = Modifier.testTag("licenses")) {
            items(libraries, key = { it.id }) { library ->
                Column(Modifier.fillMaxWidth().clickable { shown = library }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("library_${library.id}")) {
                    SafeText("${library.name} ${library.version}".trim(), maxLines = 2, style = MaterialTheme.typography.bodyLarge)
                    Text(library.licenses.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    shown?.let { library ->
        AlertDialog(
            onDismissRequest = { shown = null },
            title = { SafeText(library.name, maxLines = 2) },
            text = {
                LazyColumn(Modifier.heightIn(max = 480.dp).testTag("license_text")) { item { Text(library.text, style = MaterialTheme.typography.bodySmall) } }
            },
            confirmButton = { TextButton(onClick = { shown = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}
