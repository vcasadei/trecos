package app.trecos.ui.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trecos.AppMessage
import app.trecos.R
import app.trecos.data.Container
import app.trecos.data.TrashEntry
import app.trecos.places.Destination
import app.trecos.places.OrganizeStore
import app.trecos.places.Selection
import app.trecos.ui.appContainer
import app.trecos.ui.shell.TrecosTopBar
import app.trecos.ui.text.SafeText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Returns the test tag of a trash row.
 *
 * @param name the trashed thing's name.
 * @return the tag, such as `trash_USB hub`.
 */
fun trashTag(name: String): String = "trash_$name"

/**
 * A house's trash: what was deleted, days left before it is removed for
 * good, Restore, Delete permanently and Empty trash.
 *
 * @param houseId the house.
 * @param onBack leaves the screen.
 */
@Composable
fun TrashScreen(houseId: String, onBack: () -> Unit) {
    val app = appContainer()
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val entries by remember(houseId) { app.database.organize().observeTrash(houseId) }.collectAsStateWithLifecycle(emptyList())
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashEntry?>(null) }
    var choosing by remember { mutableStateOf<TrashEntry?>(null) }
    val now = app.clock()

    fun restore(entry: TrashEntry, destination: Destination? = null) {
        scope.launch {
            val result = app.organize.restore(entry.id, destination)
            when {
                result.needsDestination -> {
                    choosing = entry
                    app.messages.tryEmit(AppMessage(resources.getString(R.string.restore_where)))
                }
                result.qrRemoved -> app.messages.tryEmit(AppMessage(resources.getString(R.string.restored_qr_removed)))
                else -> app.messages.tryEmit(AppMessage(resources.getString(R.string.restored)))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.trash_title), onBack = onBack) {
            if (entries.isNotEmpty()) {
                TextButton(onClick = { confirmEmpty = true }, modifier = Modifier.testTag("empty_trash")) { Text(stringResource(R.string.empty_trash)) }
            }
        }
        if (entries.isEmpty()) {
            Text(stringResource(R.string.trash_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp).testTag("trash_empty"))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = BottomBarClearance)) {
            items(entries, key = { it.id }) { entry ->
                val days = ((entry.trashedAt + OrganizeStore.RETENTION_MS - now) / DAY_MS + 1).toInt().coerceAtLeast(0)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag(trashTag(entry.name)),
                ) {
                    Icon(
                        painterResource(if (entry.kind == TrashEntry.KIND_ITEM) R.drawable.ic_item else R.drawable.ic_container_box),
                        contentDescription = null,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        SafeText(entry.name, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                        Text(pluralStringResource(R.plurals.days_left, days, days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { restore(entry) }, modifier = Modifier.testTag("restore_${entry.name}")) {
                        Icon(painterResource(R.drawable.ic_restore), contentDescription = stringResource(R.string.action_restore))
                    }
                    IconButton(onClick = { confirmDelete = entry }, modifier = Modifier.testTag("purge_${entry.name}")) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete_permanently))
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            text = { Text(stringResource(R.string.empty_trash_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    scope.launch {
                        app.organize.emptyTrash(entries.map { it.id })
                        app.freeUnusedPhotos()
                    }
                }, modifier = Modifier.testTag("confirm_empty")) { Text(stringResource(R.string.empty_trash)) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(R.string.delete_permanently_confirm, entry.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        app.organize.deletePermanently(entry.id)
                        app.freeUnusedPhotos()
                    }
                }, modifier = Modifier.testTag("confirm_purge")) { Text(stringResource(R.string.delete_permanently)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    choosing?.let { entry ->
        DestinationPicker(
            startHouse = entry.houseId,
            movingContainers = emptySet(),
            confirmLabel = stringResource(R.string.action_restore),
            onPick = {
                choosing = null
                restore(entry, it)
            },
            onDismiss = { choosing = null },
        )
    }
}

/**
 * The "choose what to keep" screen of a container being deleted: tick
 * things, move them away (repeatable), and Finish to trash the container
 * with whatever is still listed. Leaving early deletes nothing.
 *
 * @param containerId the container being deleted.
 * @param onBack leaves the screen without deleting.
 * @param onFinished called after the container went to the trash.
 */
@Composable
fun KeepScreen(containerId: String, onBack: () -> Unit, onFinished: () -> Unit) {
    val app = appContainer()
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val root by remember(containerId) { app.database.containers().observe(containerId) }.collectAsStateWithLifecycle(null)
    var level by rememberSaveable { mutableStateOf(containerId) }
    var checkedItems by rememberSaveable { mutableStateOf(listOf<String>()) }
    var checkedContainers by rememberSaveable { mutableStateOf(listOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    val box = root ?: return
    val containers by remember(level) { app.database.containers().observeChildren(box.houseId, level) }.collectAsStateWithLifecycle(emptyList())
    val items by remember(level) { app.database.items().observeIn(box.houseId, level) }.collectAsStateWithLifecycle(emptyList())
    val levelContainer by remember(level) { app.database.containers().observe(level) }.collectAsStateWithLifecycle(null)
    val selection = Selection(itemIds = checkedItems, containerIds = checkedContainers)
    BackHandler(enabled = level != containerId) { level = levelContainer?.parentId ?: containerId }

    fun moveTo(destination: Destination) {
        scope.launch {
            app.organize.move(selection, destination)
            checkedItems = emptyList()
            checkedContainers = emptyList()
            app.messages.tryEmit(AppMessage(resources.getString(R.string.moved)))
        }
    }

    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.keep_title), onBack = { if (level != containerId) level = levelContainer?.parentId ?: containerId else onBack() })
        Text(stringResource(R.string.keep_body), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        SafeText(levelContainer?.name ?: box.name, maxLines = 1, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(containers, key = { it.id }) { container ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("keep_${container.name}")) {
                    Checkbox(
                        checked = container.id in checkedContainers,
                        onCheckedChange = { checkedContainers = if (it) checkedContainers + container.id else checkedContainers - container.id },
                        modifier = Modifier.testTag("keep_check_${container.name}"),
                    )
                    Icon(painterResource(PlaceIcons.container(container.icon)), contentDescription = null)
                    SafeText(container.name, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = { level = container.id }, modifier = Modifier.testTag("keep_open_${container.name}")) {
                        Icon(painterResource(R.drawable.ic_expand), contentDescription = container.name)
                    }
                }
            }
            items(items, key = { it.id }) { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        checkedItems = if (item.id in checkedItems) checkedItems - item.id else checkedItems + item.id
                    }.testTag("keep_${item.name}"),
                ) {
                    Checkbox(
                        checked = item.id in checkedItems,
                        onCheckedChange = { checkedItems = if (it) checkedItems + item.id else checkedItems - item.id },
                        modifier = Modifier.testTag("keep_check_${item.name}"),
                    )
                    Icon(painterResource(R.drawable.ic_item), contentDescription = null)
                    SafeText(item.name, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { menu = true }, enabled = !selection.isEmpty, modifier = Modifier.fillMaxWidth().testTag("move_selected")) {
                    Text(stringResource(R.string.move_selected))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.parent_level)) }, onClick = {
                        menu = false
                        moveTo(Destination(box.houseId, box.parentId))
                    }, modifier = Modifier.testTag("keep_parent"))
                    DropdownMenuItem(text = { Text(stringResource(R.string.new_container_at_parent)) }, onClick = {
                        menu = false
                        naming = true
                    }, modifier = Modifier.testTag("keep_new_container"))
                    DropdownMenuItem(text = { Text(stringResource(R.string.choose_container)) }, onClick = {
                        menu = false
                        picking = true
                    }, modifier = Modifier.testTag("keep_choose"))
                }
            }
            Button(onClick = { confirmFinish = true }, modifier = Modifier.testTag("keep_finish")) { Text(stringResource(R.string.finish)) }
        }
    }
    if (picking) {
        DestinationPicker(
            startHouse = box.houseId,
            movingContainers = setOf(containerId),
            confirmLabel = stringResource(R.string.move_here),
            otherHouses = false,
            onPick = {
                picking = false
                moveTo(it)
            },
            onDismiss = { picking = false },
        )
    }
    if (naming) {
        NameDialog(onDismiss = { naming = false }) { name ->
            naming = false
            scope.launch {
                val now = app.clock()
                val created = Container(
                    id = app.newId(), houseId = box.houseId, parentId = box.parentId, name = name,
                    icon = PlaceIcons.defaultContainer, createdAt = now, updatedAt = now,
                )
                app.database.containers().insert(created)
                moveTo(Destination(box.houseId, created.id))
            }
        }
    }
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            text = { Text(stringResource(R.string.finish_confirm, box.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    scope.launch {
                        val entries = app.organize.trash(Selection(containerIds = listOf(containerId)))
                        app.messages.tryEmit(AppMessage(resources.getString(R.string.deleted), entries))
                        onFinished()
                    }
                }, modifier = Modifier.testTag("confirm_finish")) { Text(stringResource(R.string.finish)) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * Asks for a new container's name, full screen.
 *
 * @param onDismiss closes without a name.
 * @param onName called with the typed name.
 */
@Composable
private fun NameDialog(onDismiss: () -> Unit, onName: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.new_container_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_container_name"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onName(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("create_container")) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

/**
 * Deletes a house permanently once its name is typed exactly. The last house
 * can't be deleted.
 *
 * @param houseId the house.
 * @param onBack leaves without deleting.
 * @param onDeleted called after the house was deleted.
 */
@Composable
fun DeleteHouseScreen(houseId: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val app = appContainer()
    val scope = rememberCoroutineScope()
    val house by remember(houseId) { app.database.houses().observe(houseId) }.collectAsStateWithLifecycle(null)
    var typed by rememberSaveable { mutableStateOf("") }
    val current = house ?: return
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.delete_house_title, current.name), onBack = onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.delete_house_body))
            if (app.features.driveSync && app.sync.status.collectAsStateWithLifecycle().value.state.connected) {
                Text(stringResource(R.string.delete_house_synced), modifier = Modifier.testTag("delete_house_synced"))
            }
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.type_house_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("type_house_name"),
            )
            Button(
                onClick = {
                    scope.launch {
                        app.organize.deleteHouse(houseId)
                        app.scope.launch { app.sync.houseDeleted(houseId) }
                        app.freeUnusedPhotos()
                        app.database.houses().observeAll().first().firstOrNull()?.let { app.preferences.setLastHouse(it.id) }
                        onDeleted()
                    }
                },
                enabled = typed.trim() == current.name,
                modifier = Modifier.testTag("confirm_delete_house"),
            ) { Text(stringResource(R.string.delete_permanently)) }
        }
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000
