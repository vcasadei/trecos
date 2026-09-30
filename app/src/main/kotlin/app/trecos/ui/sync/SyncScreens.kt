package app.trecos.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.trecos.AppContainer
import app.trecos.AppMessage
import app.trecos.R
import app.trecos.data.SyncFrequency
import app.trecos.places.Money
import app.trecos.sync.AuthOutcome
import app.trecos.sync.CommitMeta
import app.trecos.sync.ConflictKind
import app.trecos.sync.ConnectResult
import app.trecos.sync.StoredConflict
import app.trecos.sync.SyncWorker
import app.trecos.ui.appContainer
import app.trecos.ui.appViewModel
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.places.BottomBarClearance
import app.trecos.ui.places.formatDate
import app.trecos.ui.shell.TrecosTopBar
import app.trecos.ui.text.SafeText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Drives the sync screens.
 *
 * @param app the app's container.
 */
class SyncViewModel(private val app: AppContainer) : ViewModel() {
    private val sync = app.sync

    /** The sync status. */
    val status = sync.status

    /** How often sync runs by itself. */
    val frequency = app.preferences.syncFrequency.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncFrequency.Daily)

    /** Whether photos wait for Wi-Fi. */
    val photosOnWifi = app.preferences.photosOnlyOnWifi.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** A choice the connection is waiting for, or `null`. */
    var offer by mutableStateOf<ConnectResult?>(null)
        private set

    private var token: String? = null

    init {
        sync.refresh()
    }

    /**
     * Handles the Google screens' outcome.
     *
     * @param outcome how it ended.
     */
    fun onAuth(outcome: AuthOutcome) {
        if (outcome !is AuthOutcome.Granted) return
        token = outcome.token
        app.scope.launch {
            when (val result = sync.connect(outcome.token)) {
                is ConnectResult.OfferRestore, is ConnectResult.OfferMerge -> offer = result
                is ConnectResult.Synced -> connected()
                is ConnectResult.Failed -> app.messages.tryEmit(AppMessage(result.message))
                ConnectResult.Cancelled -> Unit
            }
        }
    }

    /**
     * Answers a restore or merge offer.
     *
     * @param bring whether to bring the Drive houses here.
     * @param replaceLocal whether to delete this device's houses first.
     */
    fun answer(bring: Boolean, replaceLocal: Boolean) {
        val current = offer ?: return
        offer = null
        val access = token ?: return
        val houses = when (current) {
            is ConnectResult.OfferRestore -> current.houses.keys
            is ConnectResult.OfferMerge -> current.houses.keys
            else -> emptySet()
        }
        app.scope.launch {
            // "Not now" still syncs this device's own houses; Drive's other houses stay in Drive.
            val result = sync.restore(access, if (bring) houses else emptySet(), replaceLocal = bring && replaceLocal)
            if (result is ConnectResult.Synced) connected() else if (result is ConnectResult.Failed) app.messages.tryEmit(AppMessage(result.message))
        }
    }

    /** Syncs now, on the app's scope so it finishes even if the screen closes. */
    fun syncNow() {
        app.scope.launch {
            if (sync.syncNow()) app.messages.tryEmit(AppMessage(app.resources.getString(R.string.sync_done)))
        }
    }

    /** Re-creates the Drive copy after it went missing. */
    fun uploadAgain() {
        app.scope.launch { sync.uploadAgain() }
    }

    /** @param value how often to sync by itself. */
    fun setFrequency(value: SyncFrequency) {
        viewModelScope.launch {
            app.preferences.setSyncFrequency(value)
            if (sync.store.load().connected) SyncWorker.schedule(app.appContext, value)
        }
    }

    /** @param on whether photos wait for Wi-Fi. */
    fun setPhotosOnWifi(on: Boolean) {
        viewModelScope.launch { app.preferences.setPhotosOnlyOnWifi(on) }
    }

    /** @param name this device's new name. */
    fun rename(name: String) = sync.rename(name)

    /** Stops syncing; the data stays. */
    fun disconnect() {
        sync.disconnect()
        SyncWorker.cancel(app.appContext)
        app.messages.tryEmit(AppMessage(app.resources.getString(R.string.sync_disconnected)))
    }

    /**
     * Resolves a conflict.
     *
     * @param conflict the conflict.
     * @param keepTheirs whether to take the other device's value.
     */
    fun resolve(conflict: StoredConflict, keepTheirs: Boolean) {
        app.scope.launch { sync.resolve(conflict, keepTheirs) }
    }

    /** @return the sync history, newest first. */
    fun history(): List<CommitMeta> = sync.engine.history()

    /** Schedules background syncs after connecting. */
    private suspend fun connected() {
        SyncWorker.schedule(app.appContext, app.preferences.syncFrequency.first())
    }
}

/**
 * Where the sync screen leads.
 *
 * @property openConflicts opens the conflicts.
 * @property openHistory opens the history.
 */
data class SyncNavigation(val openConflicts: () -> Unit, val openHistory: () -> Unit)

/**
 * Settings > Sync (spec "Connecting Google Drive", "Sync never blocks the app").
 *
 * @param nav where it leads.
 * @param onBack leaves the screen.
 */
@Composable
fun SyncScreen(nav: SyncNavigation, onBack: () -> Unit) {
    val vm = appViewModel { SyncViewModel(it) }
    val app = appContainer()
    val status by vm.status.collectAsStateWithLifecycle()
    val frequency by vm.frequency.collectAsStateWithLifecycle()
    val photosOnWifi by vm.photosOnWifi.collectAsStateWithLifecycle()
    val connect = app.sync.auth.rememberConnect(vm::onAuth)
    val state = status.state
    val language = AppLanguage.current()
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.sync_title), onBack = onBack)
        if (status.running) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("sync_running"))
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomBarClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.testTag("sync_list"),
        ) {
            if (!state.connected) {
                item { Text(stringResource(R.string.sync_intro), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                item { Button(onClick = connect, enabled = !status.running, modifier = Modifier.testTag("connect_drive")) { Text(stringResource(R.string.sync_connect)) } }
                return@LazyColumn
            }
            item { Text(stringResource(R.string.sync_connected_as, state.accountEmail), modifier = Modifier.padding(top = 8.dp).testTag("sync_account")) }
            item {
                Column(Modifier.testTag("sync_status")) {
                    Text(
                        when {
                            status.running -> stringResource(R.string.sync_running)
                            state.lastSuccess != null -> stringResource(R.string.sync_last, formatDate(state.lastSuccess, language) + " " + time(state.lastSuccess))
                            else -> stringResource(R.string.sync_never)
                        },
                    )
                    state.lastError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("sync_error"))
                        if (!state.driveMissing) Text(stringResource(R.string.sync_retry_scheduled), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.driveMissing) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.sync_error_missing))
                            Button(onClick = vm::uploadAgain, modifier = Modifier.testTag("upload_again")) { Text(stringResource(R.string.sync_missing_action)) }
                        }
                    }
                }
            }
            item { Button(onClick = vm::syncNow, enabled = !status.running, modifier = Modifier.testTag("sync_now")) { Text(stringResource(R.string.sync_now)) } }
            if (state.conflicts.isNotEmpty()) {
                item {
                    OutlinedButton(onClick = nav.openConflicts, modifier = Modifier.testTag("open_conflicts")) {
                        Text(pluralStringResource(R.plurals.sync_conflicts_count, state.conflicts.size, state.conflicts.size))
                    }
                }
            }
            item { FrequencyRow(frequency, vm::setFrequency) }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(photosOnWifi, role = Role.Switch) { vm.setPhotosOnWifi(it) }.testTag("photos_wifi"),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sync_photos_wifi))
                        Text(stringResource(R.string.sync_photos_wifi_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = photosOnWifi, onCheckedChange = null)
                }
            }
            item {
                var name by remember(state.deviceName) { mutableStateOf(state.deviceName) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.sync_device_name)) }, modifier = Modifier.weight(1f).testTag("device_name"))
                    TextButton(onClick = { vm.rename(name) }, enabled = name.isNotBlank() && name != state.deviceName, modifier = Modifier.testTag("save_device_name")) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
            item { OutlinedButton(onClick = nav.openHistory, modifier = Modifier.testTag("open_history")) { Text(stringResource(R.string.sync_history)) } }
            item { TextButton(onClick = vm::disconnect, modifier = Modifier.testTag("disconnect")) { Text(stringResource(R.string.sync_disconnect)) } }
        }
    }
    when (val offer = vm.offer) {
        is ConnectResult.OfferRestore -> AlertDialog(
            onDismissRequest = { vm.answer(bring = false, replaceLocal = false) },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_body, offer.houses.values.joinToString(", ")), modifier = Modifier.testTag("restore_offer")) },
            confirmButton = { TextButton(onClick = { vm.answer(bring = true, replaceLocal = true) }, modifier = Modifier.testTag("restore")) { Text(stringResource(R.string.restore_action)) } },
            dismissButton = { TextButton(onClick = { vm.answer(bring = false, replaceLocal = false) }) { Text(stringResource(R.string.restore_not_now)) } },
        )
        is ConnectResult.OfferMerge -> AlertDialog(
            onDismissRequest = { vm.answer(bring = false, replaceLocal = false) },
            title = { Text(stringResource(R.string.merge_title)) },
            text = { Text(stringResource(R.string.merge_body, offer.houses.values.joinToString(", ")), modifier = Modifier.testTag("merge_offer")) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { vm.answer(bring = true, replaceLocal = false) }, modifier = Modifier.testTag("merge")) { Text(stringResource(R.string.merge_action)) }
                    TextButton(onClick = { vm.answer(bring = true, replaceLocal = true) }, modifier = Modifier.testTag("keep_drive")) { Text(stringResource(R.string.keep_drive_action)) }
                }
            },
            dismissButton = { TextButton(onClick = { vm.answer(bring = false, replaceLocal = false) }) { Text(stringResource(R.string.action_cancel)) } },
        )
        else -> Unit
    }
}

/** The frequency choice. */
@Composable
private fun FrequencyRow(selected: SyncFrequency, onSelect: (SyncFrequency) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val labels = mapOf(
        SyncFrequency.Daily to stringResource(R.string.frequency_daily),
        SyncFrequency.Every5Days to stringResource(R.string.frequency_5),
        SyncFrequency.Every15Days to stringResource(R.string.frequency_15),
        SyncFrequency.Every30Days to stringResource(R.string.frequency_30),
        SyncFrequency.Never to stringResource(R.string.frequency_never),
    )
    Box {
        Column(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp).testTag("sync_frequency")) {
            Text(stringResource(R.string.sync_frequency))
            Text(labels.getValue(selected), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            labels.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    open = false
                    onSelect(value)
                }, modifier = Modifier.testTag("frequency_$value"))
            }
        }
    }
}

/**
 * @param epochMillis a time.
 * @return it as hours and minutes on this phone.
 */
private fun time(epochMillis: Long): String = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/**
 * The conflicts, each with both values and "Keep mine" / "Keep theirs"
 * (spec "Merging and conflicts").
 *
 * @param onBack leaves the screen.
 */
@Composable
fun ConflictsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SyncViewModel(it) }
    val status by vm.status.collectAsStateWithLifecycle()
    val conflicts = status.state.conflicts
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.conflicts_title), onBack = onBack)
        if (conflicts.isEmpty()) Text(stringResource(R.string.conflicts_none), modifier = Modifier.padding(16.dp).testTag("no_conflicts"))
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, BottomBarClearance), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(conflicts, key = { it.houseId + it.conflict.key + it.conflict.field }) { stored ->
                ConflictCard(stored, onResolve = { theirs -> vm.resolve(stored, theirs) })
            }
        }
    }
}

/** One conflict with its two choices. */
@Composable
private fun ConflictCard(stored: StoredConflict, onResolve: (keepTheirs: Boolean) -> Unit) {
    val conflict = stored.conflict
    val app = appContainer()
    val thing = produceState(conflict.rowKey, conflict) {
        val row = (conflict.mine as? JsonObject) ?: (conflict.theirs as? JsonObject)
        value = row?.get("name")?.let(::plain) ?: when (conflict.table) {
            "item" -> app.database.organize().itemAnyState(conflict.rowKey)?.name
            "container" -> app.database.organize().containerAnyState(conflict.rowKey)?.name
            else -> null
        } ?: conflict.rowKey
    }.value
    val device = stored.theirDevice.ifEmpty { stringResource(R.string.conflict_other_device) }
    Card(Modifier.fillMaxWidth().testTag("conflict_${conflict.key}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SafeText(thing, maxLines = 2, style = MaterialTheme.typography.titleSmall)
            when (conflict.kind) {
                ConflictKind.SameField -> {
                    val mine = shown(conflict.field, conflict.mine)
                    val theirs = shown(conflict.field, conflict.theirs)
                    Text(stringResource(R.string.conflict_same_field, fieldLabel(conflict.field), mine, theirs), modifier = Modifier.testTag("conflict_question"))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { onResolve(false) }, modifier = Modifier.fillMaxWidth().testTag("keep_mine")) {
                            SafeText("${stringResource(R.string.conflict_keep_mine)} ($mine)", maxLines = 2)
                        }
                        OutlinedButton(onClick = { onResolve(true) }, modifier = Modifier.fillMaxWidth().testTag("keep_theirs")) {
                            SafeText("${stringResource(R.string.conflict_keep_theirs)} ($theirs)", maxLines = 2)
                        }
                    }
                }
                ConflictKind.EditVersusDelete -> {
                    val here = conflict.deletedHere
                    Text(stringResource(if (here) R.string.conflict_deleted_here else R.string.conflict_deleted_there, device), modifier = Modifier.testTag("conflict_question"))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Deleted here: deleting keeps mine. Deleted there: keeping keeps mine.
                        OutlinedButton(onClick = { onResolve(!here) }, modifier = Modifier.fillMaxWidth().testTag("conflict_delete")) { Text(stringResource(R.string.conflict_delete)) }
                        OutlinedButton(onClick = { onResolve(here) }, modifier = Modifier.fillMaxWidth().testTag("conflict_keep")) { Text(stringResource(R.string.conflict_keep)) }
                    }
                }
            }
        }
    }
}

/** @return a field's name for people. */
@Composable
private fun fieldLabel(field: String?): String = when (field) {
    "name" -> stringResource(R.string.field_name)
    "quantity" -> stringResource(R.string.field_quantity)
    "unitPrice" -> stringResource(R.string.field_unit_price)
    "description" -> stringResource(R.string.field_description)
    "brand" -> stringResource(R.string.field_brand)
    "model" -> stringResource(R.string.field_model)
    "serial" -> stringResource(R.string.field_serial)
    "qrCode" -> stringResource(R.string.field_qr)
    "containerId", "parentId" -> stringResource(R.string.field_location)
    "deletedAt" -> stringResource(R.string.field_in_trash)
    "value" -> stringResource(R.string.field_value)
    else -> field.orEmpty()
}

/** @return a value as people read it. */
@Composable
private fun shown(field: String?, value: JsonElement?): String {
    if (value == null || value is JsonNull) return stringResource(R.string.value_empty)
    if (field == "unitPrice" || field == "valueOverride") {
        val minor = (value as? JsonPrimitive)?.content?.toLongOrNull()
        if (minor != null) {
            val currency by appContainer().preferences.currency.collectAsStateWithLifecycle("USD")
            return Money.format(minor, currency, AppLanguage.current())
        }
    }
    return plain(value)
}

/** @return a JSON value's plain text. */
private fun plain(value: JsonElement): String = (value as? JsonPrimitive)?.content ?: value.toString()

/**
 * The sync history (spec "Sync history"): time, Google user, device and a summary.
 *
 * @param onBack leaves the screen.
 */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val vm = appViewModel { SyncViewModel(it) }
    val entries = remember { vm.history() }
    val language = AppLanguage.current()
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.sync_history), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, BottomBarClearance), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { entry ->
                val parts = listOfNotNull(
                    DateTimeFormatter.ofPattern("d MMM HH:mm", Money.localeOf(language)).format(Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault())),
                    entry.userName.ifEmpty { null },
                    entry.deviceName.ifEmpty { null },
                    pluralStringResource(R.plurals.history_changes, entry.changes, entry.changes),
                    if (entry.conflicts > 0) pluralStringResource(R.plurals.history_conflicts, entry.conflicts, entry.conflicts) else null,
                )
                SafeText(parts.joinToString(", "), maxLines = 2, modifier = Modifier.fillMaxWidth().testTag("history_${entry.id}"))
                if (entry.userEmail.isNotEmpty()) Text(entry.userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
