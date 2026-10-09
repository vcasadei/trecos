package app.trecos.ui.places

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.trecos.AppContainer
import app.trecos.AppMessage
import app.trecos.R
import app.trecos.places.Destination
import app.trecos.places.QrClash
import app.trecos.places.Selection
import app.trecos.ui.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * An open destination picker.
 *
 * @property move `true` to move, `false` to copy.
 * @property selection what is moved or copied.
 * @property startHouse the house the picker starts in.
 * @property onDone called after the action succeeded.
 */
data class PickRequest(val move: Boolean, val selection: Selection, val startHouse: String, val onDone: () -> Unit)

/**
 * A move waiting for QR clashes to be resolved one by one.
 *
 * @property selection what is moved.
 * @property destination where it goes.
 * @property pending clashes still to resolve; the first one is shown.
 * @property resolved codes already decided: id to new code, or `null` to remove.
 * @property onDone called after the move.
 */
data class ClashRequest(
    val selection: Selection,
    val destination: Destination,
    val pending: List<QrClash>,
    val resolved: Map<String, String?>,
    val onDone: () -> Unit,
)

/**
 * A delete waiting for confirmation.
 *
 * @property selection what is deleted.
 * @property name the single thing's name, or `null` for several.
 * @property count how many things are selected.
 * @property contents for a single container, how many things are inside it; 0 otherwise.
 * @property onDeleted called after the delete, for example to leave the deleted thing's screen.
 */
data class DeleteRequest(val selection: Selection, val name: String?, val count: Int, val contents: Int, val onDeleted: () -> Unit)

/**
 * Runs Move, Copy, Duplicate and Delete for place and item screens, with
 * their dialogs ([OrganizeDialogs]).
 *
 * @param app the app's container.
 * @param scope a scope tied to the screen.
 * @param resources for message texts.
 */
class OrganizeController(private val app: AppContainer, private val scope: CoroutineScope, private val resources: Resources) {

    /** The open destination picker, or `null`. */
    var picking by mutableStateOf<PickRequest?>(null)

    /** The QR clash being resolved, or `null`. */
    var clash by mutableStateOf<ClashRequest?>(null)

    /** The delete being confirmed, or `null`. */
    var deleting by mutableStateOf<DeleteRequest?>(null)

    /**
     * Opens the picker to move things.
     *
     * @param selection what to move.
     * @param startHouse the house to start browsing in.
     * @param onDone called after the move.
     */
    fun move(selection: Selection, startHouse: String, onDone: () -> Unit = {}) {
        picking = PickRequest(move = true, selection = selection, startHouse = startHouse, onDone = onDone)
    }

    /**
     * Opens the picker to copy things.
     *
     * @param selection what to copy.
     * @param startHouse the house to start browsing in.
     * @param onDone called after the copy.
     */
    fun copy(selection: Selection, startHouse: String, onDone: () -> Unit = {}) {
        picking = PickRequest(move = false, selection = selection, startHouse = startHouse, onDone = onDone)
    }

    /**
     * Duplicates one item or container in its place and opens the copy.
     *
     * @param id the item or container.
     * @param open called with the copy's id, to open its edit form.
     */
    fun duplicate(id: String, open: (String) -> Unit) {
        scope.launch { open(app.organize.duplicate(id, resources.getString(R.string.copy_suffix))) }
    }

    /**
     * Asks to confirm a delete.
     *
     * @param selection what to delete.
     * @param name the single thing's name, or `null` for several.
     * @param onDeleted called after the delete.
     */
    fun delete(selection: Selection, name: String?, onDeleted: () -> Unit = {}) {
        scope.launch {
            val single = selection.containerIds.singleOrNull()?.takeIf { selection.itemIds.isEmpty() }
            val contents = single?.let { app.organize.contentCount(it) } ?: 0
            deleting = DeleteRequest(selection, name, selection.itemIds.size + selection.containerIds.size, contents, onDeleted)
        }
    }

    /**
     * Continues after a destination was picked: copies, or moves after checking QR clashes.
     *
     * @param request the picker request.
     * @param destination the chosen destination.
     */
    fun onPicked(request: PickRequest, destination: Destination) {
        picking = null
        scope.launch {
            if (request.move) {
                val clashes = app.organize.qrClashes(request.selection, destination)
                if (clashes.isEmpty()) {
                    app.organize.move(request.selection, destination)
                    app.messages.tryEmit(AppMessage(resources.getString(R.string.moved)))
                    request.onDone()
                } else {
                    clash = ClashRequest(request.selection, destination, clashes, emptyMap(), request.onDone)
                }
            } else {
                app.organize.copy(request.selection, destination)
                app.messages.tryEmit(AppMessage(resources.getString(R.string.copied)))
                request.onDone()
            }
        }
    }

    /**
     * Resolves the first pending clash; moves when none are left.
     *
     * @param request the clash request.
     * @param newCode the new code, or `null` to remove it.
     */
    fun resolve(request: ClashRequest, newCode: String?) {
        val first = request.pending.first()
        val resolved = request.resolved + (first.id to newCode?.trim()?.ifEmpty { null })
        val rest = request.pending.drop(1)
        if (rest.isNotEmpty()) {
            clash = request.copy(pending = rest, resolved = resolved)
            return
        }
        clash = null
        scope.launch {
            val stillClashing = app.organize.qrClashes(request.selection, request.destination).filter { c -> resolved[c.id] == c.code }
            if (stillClashing.isNotEmpty()) {
                clash = request.copy(pending = stillClashing, resolved = resolved - stillClashing.map { it.id }.toSet())
                return@launch
            }
            app.organize.move(request.selection, request.destination, resolved)
            app.messages.tryEmit(AppMessage(resources.getString(R.string.moved)))
            request.onDone()
        }
    }

    /**
     * Moves the confirmed things to the trash and offers Undo.
     *
     * @param request the confirmed delete.
     */
    fun confirmDelete(request: DeleteRequest) {
        deleting = null
        scope.launch {
            val entries = app.organize.trash(request.selection)
            app.messages.tryEmit(AppMessage(resources.getString(R.string.deleted), entries))
            request.onDeleted()
        }
    }
}

/**
 * Remembers an [OrganizeController] for the screen.
 *
 * @return the controller.
 */
@Composable
fun rememberOrganizeController(): OrganizeController {
    val app = appContainer()
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    return remember(app, scope) { OrganizeController(app, scope, resources) }
}

/**
 * The dialogs of an [OrganizeController]: the destination picker, QR clash
 * resolution and delete confirmation.
 *
 * @param controller the controller.
 * @param onChooseWhatToKeep opens the keep screen for a container being deleted.
 */
@Composable
fun OrganizeDialogs(controller: OrganizeController, onChooseWhatToKeep: (String) -> Unit) {
    controller.picking?.let { request ->
        DestinationPicker(
            startHouse = request.startHouse,
            movingContainers = if (request.move) request.selection.containerIds.toSet() else emptySet(),
            confirmLabel = stringResource(if (request.move) R.string.move_here else R.string.copy_here),
            onPick = { controller.onPicked(request, it) },
            onDismiss = { controller.picking = null },
        )
    }
    controller.clash?.let { request -> QrClashDialog(request, controller) }
    controller.deleting?.let { request ->
        if (request.contents > 0) {
            AlertDialog(
                onDismissRequest = { controller.deleting = null },
                title = { Text(stringResource(R.string.delete_container_title, request.name.orEmpty())) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pluralStringResource(R.plurals.delete_container_body, request.contents, request.contents))
                        Button(onClick = { controller.confirmDelete(request) }, modifier = Modifier.fillMaxWidth().testTag("delete_everything")) {
                            Text(stringResource(R.string.delete_everything))
                        }
                        OutlinedButton(
                            onClick = {
                                controller.deleting = null
                                onChooseWhatToKeep(request.selection.containerIds.single())
                            },
                            modifier = Modifier.fillMaxWidth().testTag("choose_what_to_keep"),
                        ) { Text(stringResource(R.string.choose_what_to_keep)) }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { controller.deleting = null }, modifier = Modifier.testTag("cancel_delete")) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { controller.deleting = null },
                title = {
                    Text(
                        request.name?.let { stringResource(R.string.delete_item_title, it) }
                            ?: pluralStringResource(R.plurals.delete_selection_title, request.count, request.count),
                        modifier = Modifier.testTag("delete_title"),
                    )
                },
                text = { Text(stringResource(R.string.delete_item_body)) },
                confirmButton = {
                    TextButton(onClick = { controller.confirmDelete(request) }, modifier = Modifier.testTag("confirm_delete")) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { controller.deleting = null }, modifier = Modifier.testTag("cancel_delete")) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}

/**
 * Explains a QR clash and offers to change the code, remove it, or cancel
 * the whole move. Full screen, because small dialogs with text fields
 * misbehave in the test runner.
 *
 * @param request the clash request; its first pending clash is shown.
 * @param controller the controller.
 */
@Composable
private fun QrClashDialog(request: ClashRequest, controller: OrganizeController) {
    val first = request.pending.first()
    var code by remember(first.id) { mutableStateOf(first.code) }
    Dialog(onDismissRequest = { controller.clash = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(24.dp)
                .testTag("qr_clash"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.qr_clash_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.qr_clash_body, first.name, first.code))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.field_qr)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("clash_code"),
            )
            Button(onClick = { controller.resolve(request, code) }, enabled = code.isNotBlank() && code.trim() != first.code, modifier = Modifier.fillMaxWidth().testTag("clash_edit")) {
                Text(stringResource(R.string.qr_clash_edit))
            }
            OutlinedButton(onClick = { controller.resolve(request, null) }, modifier = Modifier.fillMaxWidth().testTag("clash_remove")) {
                Text(stringResource(R.string.qr_clash_remove))
            }
            TextButton(onClick = { controller.clash = null }, modifier = Modifier.testTag("clash_cancel")) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
