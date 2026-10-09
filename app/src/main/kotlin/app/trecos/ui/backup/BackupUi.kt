package app.trecos.ui.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.trecos.AppContainer
import app.trecos.AppMessage
import app.trecos.R
import app.trecos.backup.BackupContents
import app.trecos.backup.BackupExporter
import app.trecos.backup.BackupImporter
import app.trecos.backup.BackupLayout
import app.trecos.backup.BackupProblem
import app.trecos.ui.appViewModel
import app.trecos.ui.places.BottomBarClearance
import app.trecos.ui.shell.TrecosTopBar
import app.trecos.ui.text.SafeText
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where backups are saved and opened; replaced by a fake in tests, which can't drive the system file picker. */
interface BackupFiles {
    /**
     * Creates the pickers for one screen.
     *
     * @param onCreated called with where to save a new backup, or `null` when cancelled.
     * @param onOpened called with the backup to import, or `null` when cancelled.
     * @return launchers: `first` asks where to save a file with the given name, `second` asks for a file to open.
     */
    @Composable
    fun rememberPickers(onCreated: (Uri?) -> Unit, onOpened: (Uri?) -> Unit): Pair<(String) -> Unit, () -> Unit>

    /**
     * @param context a context.
     * @param uri a picked location.
     * @return a stream writing there.
     */
    fun openOutput(context: Context, uri: Uri): OutputStream

    /**
     * @param context a context.
     * @param uri a picked file.
     * @return a stream reading it.
     */
    fun openInput(context: Context, uri: Uri): InputStream

    /**
     * Removes an incomplete file.
     *
     * @param context a context.
     * @param uri the file.
     */
    fun delete(context: Context, uri: Uri)
}

/** The system file picker (Storage Access Framework, design D15); no storage permission. */
object SystemBackupFiles : BackupFiles {
    @Composable
    override fun rememberPickers(onCreated: (Uri?) -> Unit, onOpened: (Uri?) -> Unit): Pair<(String) -> Unit, () -> Unit> {
        val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), onCreated)
        val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onOpened)
        return remember(create, open) { { name: String -> create.launch(name) } to { open.launch(arrayOf("application/zip", "application/octet-stream")) } }
    }

    override fun openOutput(context: Context, uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write $uri")

    override fun openInput(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("Can't read $uri")

    override fun delete(context: Context, uri: Uri) {
        runCatching {
            if (uri.scheme == "file") File(requireNotNull(uri.path)).delete() else DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
    }
}

/** Where backups go; tests provide a fake. */
val LocalBackupFiles = staticCompositionLocalOf<BackupFiles> { SystemBackupFiles }

/**
 * Runs exports and imports and keeps the import preview.
 *
 * @param app the app's container.
 */
class BackupViewModel(private val app: AppContainer) : ViewModel() {
    private val exporter = BackupExporter(app.database, app.photoStore)
    private val importer = BackupImporter(app.database, app.photoStore, app.workDir, app.newId)

    /** Every house, for choosing what to export. */
    val houses = app.database.houses().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether an export or import is running. */
    var busy by mutableStateOf(false)
        private set

    /** A read backup waiting for the user's choice, or `null`. */
    var preview by mutableStateOf<BackupContents?>(null)
        private set

    /**
     * @return the suggested file name for today.
     */
    fun fileName(): String = BackupLayout.fileName(app.clock())

    /**
     * Writes a backup; on failure the incomplete file is removed.
     *
     * @param context a context.
     * @param files the file access.
     * @param uri where to save it.
     * @param houseIds the houses to include.
     * @param version the app version.
     */
    fun export(context: Context, files: BackupFiles, uri: Uri, houseIds: Set<String>, version: String) {
        busy = true
        viewModelScope.launch {
            val ok = runCatching { exporter.export(houseIds, files.openOutput(context, uri), app.clock(), version) }.isSuccess
            if (!ok) files.delete(context, uri)
            busy = false
            message(if (ok) R.string.export_done else R.string.export_failed)
        }
    }

    /**
     * Reads a backup for the preview.
     *
     * @param context a context.
     * @param files the file access.
     * @param uri the picked file.
     */
    fun open(context: Context, files: BackupFiles, uri: Uri) {
        busy = true
        viewModelScope.launch {
            try {
                preview = importer.read(files.openInput(context, uri))
            } catch (e: BackupProblem.Newer) {
                message(R.string.import_newer)
            } catch (e: Exception) {
                message(R.string.import_invalid)
            } finally {
                busy = false
            }
        }
    }

    /**
     * Imports the previewed backup.
     *
     * @param replace `true` for "Replace everything", `false` for "Add as new houses".
     */
    fun import(replace: Boolean) {
        val contents = preview ?: return
        preview = null
        busy = true
        viewModelScope.launch {
            val ok = runCatching {
                if (replace) {
                    importer.replaceEverything(contents)
                    app.preferences.setLastHouse(contents.houses.first().house.id)
                } else {
                    importer.addAsNewHouses(contents)
                }
            }.isSuccess
            busy = false
            message(if (ok) R.string.import_done else R.string.import_invalid)
        }
    }

    /** Drops the preview without importing. */
    fun cancelPreview() {
        preview?.discard()
        preview = null
    }

    override fun onCleared() {
        preview?.discard()
    }

    /** Shows a message app-wide. */
    private fun message(text: Int) {
        app.messages.tryEmit(AppMessage(app.resources.getString(text)))
    }
}

/**
 * Export and import (backup spec): choose houses (all by default), save
 * with the system file picker; open a backup, preview it, then add it as new
 * houses or replace everything after confirming.
 *
 * @param onBack leaves the screen.
 */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val vm = appViewModel { BackupViewModel(it) }
    val houses by vm.houses.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val files = LocalBackupFiles.current
    var excluded by remember { mutableStateOf(setOf<String>()) }
    var confirmReplace by remember { mutableStateOf(false) }
    var warnUnencrypted by remember { mutableStateOf(false) }
    val encrypted = encryptedHere()
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val (create, open) = files.rememberPickers(
        onCreated = { uri -> uri?.let { vm.export(context, files, it, houses.map { h -> h.id }.toSet() - excluded, version) } },
        onOpened = { uri -> uri?.let { vm.open(context, files, it) } },
    )
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.settings_backup), onBack = onBack)
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("backup_busy"))
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomBarClearance), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            item { Text(stringResource(R.string.export_intro), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(houses, key = { it.id }) { house ->
                val checked = house.id !in excluded
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable { excluded = if (checked) excluded + house.id else excluded - house.id }
                        .testTag("export_house_${house.id}"),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    SafeText(house.name, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
            item {
                Button(
                    onClick = { if (encrypted) warnUnencrypted = true else create(vm.fileName()) },
                    enabled = !vm.busy && houses.any { it.id !in excluded },
                    modifier = Modifier.padding(vertical = 8.dp).testTag("export"),
                ) { Text(stringResource(R.string.action_export)) }
            }
            item { Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
            item { Text(stringResource(R.string.import_intro), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                OutlinedButton(onClick = open, enabled = !vm.busy, modifier = Modifier.padding(vertical = 8.dp).testTag("import")) {
                    Text(stringResource(R.string.action_import))
                }
            }
        }
    }
    vm.preview?.let { contents ->
        AlertDialog(
            onDismissRequest = vm::cancelPreview,
            title = { Text(stringResource(R.string.import_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("import_preview")) {
                    contents.manifest.houses.forEach { house ->
                        Text(
                            "${house.name}: " + pluralStringResource(R.plurals.preview_containers, house.containers, house.containers) + " · " +
                                pluralStringResource(R.plurals.preview_items, house.items, house.items) + " · " +
                                pluralStringResource(R.plurals.preview_photos, house.photos, house.photos),
                        )
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { vm.import(replace = false) }, modifier = Modifier.testTag("import_add")) { Text(stringResource(R.string.import_add)) }
                    TextButton(onClick = { confirmReplace = true }, modifier = Modifier.testTag("import_replace")) { Text(stringResource(R.string.import_replace)) }
                    TextButton(onClick = vm::cancelPreview) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }
    if (warnUnencrypted) {
        AlertDialog(
            onDismissRequest = { warnUnencrypted = false },
            title = { Text(stringResource(R.string.export_unencrypted_title)) },
            text = { Text(stringResource(R.string.export_unencrypted_body), modifier = Modifier.testTag("export_unencrypted")) },
            confirmButton = {
                TextButton(onClick = {
                    warnUnencrypted = false
                    create(vm.fileName())
                }, modifier = Modifier.testTag("export_anyway")) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = { TextButton(onClick = { warnUnencrypted = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            text = {
                val synced = syncConnected()
                val warning = stringResource(R.string.import_replace_confirm) + if (synced) " " + stringResource(R.string.import_replace_sync_warning) else ""
                Text(warning, modifier = Modifier.testTag("replace_confirm"))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    vm.import(replace = true)
                }, modifier = Modifier.testTag("confirm_replace")) { Text(stringResource(R.string.import_replace)) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** @return whether Google Drive sync is connected, so "Replace everything" reaches other devices (spec "Import with sync on"). */
@Composable
private fun syncConnected(): Boolean {
    val app = app.trecos.ui.appContainer()
    if (!app.features.driveSync) return false
    val status by app.sync.status.collectAsStateWithLifecycle()
    return status.state.connected
}

/** @return whether this device's database is encrypted, so exports need a warning (spec "Export"). */
@Composable
private fun encryptedHere(): Boolean {
    val app = app.trecos.ui.appContainer()
    if (!app.features.encryption) return false
    val status by app.encryption.status.collectAsStateWithLifecycle()
    return status.on
}
