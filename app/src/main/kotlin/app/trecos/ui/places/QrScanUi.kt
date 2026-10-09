package app.trecos.ui.places

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trecos.AppContainer
import app.trecos.AppMessage
import app.trecos.R
import app.trecos.data.QrMatch
import app.trecos.places.Destination
import app.trecos.ui.appContainer
import app.trecos.ui.text.SafeText
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** The outcome of one scan. */
sealed interface ScanResult {
    /**
     * A code was read.
     *
     * @property text the code exactly as scanned; callers trim surrounding whitespace.
     */
    data class Code(val text: String) : ScanResult

    /** The user closed the scanner. */
    data object Cancelled : ScanResult

    /** The scanner couldn't run, usually because its module isn't downloaded and the phone is offline. */
    data object Unavailable : ScanResult
}

/** Reads one QR code; replaced by a fake in tests. */
interface QrScanner {
    /**
     * Opens the scanner and waits for a code.
     *
     * @param context an activity context.
     * @return what happened.
     */
    suspend fun scan(context: Context): ScanResult
}

/** The Google code scanner (design D9): QR codes only, no camera permission. */
object GoogleQrScanner : QrScanner {
    override suspend fun scan(context: Context): ScanResult = suspendCancellableCoroutine { continuation ->
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val text = barcode.rawValue?.trim()
                continuation.resume(if (text.isNullOrEmpty()) ScanResult.Cancelled else ScanResult.Code(text))
            }
            .addOnCanceledListener { continuation.resume(ScanResult.Cancelled) }
            .addOnFailureListener { error ->
                val cancelled = (error as? MlKitException)?.errorCode == MlKitException.CODE_SCANNER_CANCELLED
                continuation.resume(if (cancelled) ScanResult.Cancelled else ScanResult.Unavailable)
            }
    }
}

/** The scanner in use; tests provide a fake. */
val LocalQrScanner = staticCompositionLocalOf<QrScanner> { GoogleQrScanner }

/** What a scan found, waiting for the user. */
sealed interface ScanOutcome {
    /**
     * The code is held in several houses.
     *
     * @property matches one match per house.
     */
    data class ChooseHouse(val matches: List<QrMatch>) : ScanOutcome

    /**
     * The code belongs to something in the trash.
     *
     * @property match the trashed item or container.
     */
    data class InTrash(val match: QrMatch) : ScanOutcome

    /**
     * Nothing has the code.
     *
     * @property code the scanned code.
     */
    data class Unknown(val code: String) : ScanOutcome

    /**
     * Choosing where to create something for an unknown code.
     *
     * @property code the scanned code.
     * @property item `true` for an item, `false` for a container.
     */
    data class Placing(val code: String, val item: Boolean) : ScanOutcome
}

/**
 * Looks up scanned codes (spec "Lookup after scanning"): one match opens it,
 * several houses ask which, a trashed match offers Restore, and an unknown
 * code offers to create an item or container there.
 *
 * @param app the app's container.
 * @param scope a scope tied to the screen.
 * @param nav navigation actions.
 */
class ScanController(private val app: AppContainer, private val scope: CoroutineScope, private val nav: PlaceNavigation) {

    /** What the last scan is waiting on, or `null`. */
    var outcome by mutableStateOf<ScanOutcome?>(null)

    /**
     * Scans and looks the code up. Cancelling changes nothing.
     *
     * @param scanner the scanner.
     * @param context an activity context.
     */
    fun scan(scanner: QrScanner, context: Context) {
        scope.launch {
            when (val result = scanner.scan(context)) {
                is ScanResult.Code -> lookup(result.text.trim())
                ScanResult.Cancelled -> Unit
                ScanResult.Unavailable -> app.messages.tryEmit(AppMessage(app.resources.getString(R.string.scan_unavailable)))
            }
        }
    }

    /**
     * Handles a scanned code.
     *
     * @param code the exact code.
     */
    suspend fun lookup(code: String) {
        val matches = app.database.qr().lookup(code)
        val active = matches.filter { it.deletedAt == null }
        outcome = when {
            active.size == 1 -> {
                open(active.single())
                null
            }
            active.size > 1 -> ScanOutcome.ChooseHouse(active)
            matches.isNotEmpty() -> ScanOutcome.InTrash(matches.first())
            else -> ScanOutcome.Unknown(code)
        }
    }

    /**
     * Opens a match's screen.
     *
     * @param match the item or container.
     */
    fun open(match: QrMatch) {
        outcome = null
        if (match.kind == "item") nav.openItem(match.id) else nav.openContainer(match.houseId, match.id)
    }

    /**
     * Restores a trashed match (with whatever was trashed with it) and opens it.
     *
     * @param match the trashed item or container.
     */
    fun restore(match: QrMatch) {
        outcome = null
        scope.launch {
            val entry = app.database.organize().observeTrash(match.houseId).first()
                .firstOrNull { it.targetId == match.id || it.trashedAt == match.deletedAt }
            if (entry != null && !app.organize.restore(entry.id).needsDestination) open(match.copy(deletedAt = null))
            else nav.openTrash(match.houseId)
        }
    }

    /**
     * Opens the form for a new item or container at the chosen place, with the code and name filled in.
     *
     * @param placing the pending creation.
     * @param destination where it goes.
     */
    fun create(placing: ScanOutcome.Placing, destination: Destination) {
        outcome = null
        if (placing.item) nav.addItemWithCode(destination.houseId, destination.containerId, placing.code)
        else nav.addContainerWithCode(destination.houseId, destination.containerId, placing.code)
    }
}

/**
 * Remembers a [ScanController] for the screen.
 *
 * @param nav navigation actions.
 * @return the controller.
 */
@Composable
fun rememberScanController(nav: PlaceNavigation): ScanController {
    val app = appContainer()
    val scope = rememberCoroutineScope()
    return remember(app, scope) { ScanController(app, scope, nav) }
}

/**
 * The dialogs after a scan: choose a house, restore from the trash, or create.
 *
 * @param controller the controller.
 */
@Composable
fun ScanDialogs(controller: ScanController) {
    val app = appContainer()
    when (val outcome = controller.outcome) {
        is ScanOutcome.ChooseHouse -> AlertDialog(
            onDismissRequest = { controller.outcome = null },
            title = { Text(stringResource(R.string.choose_house_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    outcome.matches.forEach { match ->
                        val houseName = remember(match.houseId) { mutableStateOf("") }
                        androidx.compose.runtime.LaunchedEffect(match.houseId) { houseName.value = app.database.houses().get(match.houseId)?.name.orEmpty() }
                        SafeText(
                            "${houseName.value}: ${match.name}",
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().clickable { controller.open(match) }.padding(vertical = 12.dp).testTag("choose_${match.houseId}"),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { controller.outcome = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        is ScanOutcome.InTrash -> AlertDialog(
            onDismissRequest = { controller.outcome = null },
            text = { Text(stringResource(R.string.trashed_notice, outcome.match.name), modifier = Modifier.testTag("trashed_notice")) },
            confirmButton = {
                TextButton(onClick = { controller.restore(outcome.match) }, modifier = Modifier.testTag("scan_restore")) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = { TextButton(onClick = { controller.outcome = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        is ScanOutcome.Unknown -> AlertDialog(
            onDismissRequest = { controller.outcome = null },
            title = { Text(stringResource(R.string.unknown_code_title, outcome.code), modifier = Modifier.testTag("unknown_code")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.outcome = ScanOutcome.Placing(outcome.code, item = true) }, modifier = Modifier.fillMaxWidth().testTag("create_item")) {
                        Text(stringResource(R.string.create_item))
                    }
                    OutlinedButton(onClick = { controller.outcome = ScanOutcome.Placing(outcome.code, item = false) }, modifier = Modifier.fillMaxWidth().testTag("create_container")) {
                        Text(stringResource(R.string.create_container))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { controller.outcome = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        is ScanOutcome.Placing -> {
            val lastHouse by app.preferences.lastHouseId.collectAsStateCompat()
            val start = lastHouse
            if (start != null) {
                DestinationPicker(
                    startHouse = start,
                    movingContainers = emptySet(),
                    confirmLabel = stringResource(if (outcome.item) R.string.create_item else R.string.create_container),
                    onPick = { controller.create(outcome, it) },
                    onDismiss = { controller.outcome = null },
                )
            }
        }
        null -> Unit
    }
}

/**
 * Collects a flow of the last-used house, falling back to the first house.
 *
 * @return the house id to start the picker in, once known.
 */
@Composable
private fun kotlinx.coroutines.flow.Flow<String?>.collectAsStateCompat(): androidx.compose.runtime.State<String?> {
    val app = appContainer()
    return androidx.compose.runtime.produceState<String?>(null, this) {
        value = first() ?: app.database.houses().observeAll().first().firstOrNull()?.id
    }
}

/**
 * Scans a label from the item form: returns the code, or `null` when cancelled or unavailable.
 *
 * @param scanner the scanner.
 * @param context an activity context.
 * @param app the app's container, for the offline message.
 * @return the scanned code, or `null`.
 */
suspend fun scanForForm(scanner: QrScanner, context: Context, app: AppContainer): String? =
    when (val result = scanner.scan(context)) {
        is ScanResult.Code -> result.text.trim()
        ScanResult.Cancelled -> null
        ScanResult.Unavailable -> {
            app.messages.tryEmit(AppMessage(app.resources.getString(R.string.scan_unavailable)))
            null
        }
    }
