package app.trecos.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trecos.R
import app.trecos.data.DetailExtras
import app.trecos.data.FieldDef
import app.trecos.data.FieldType
import app.trecos.ui.appContainer
import app.trecos.ui.appViewModel
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.places.BottomBarClearance
import app.trecos.ui.shell.TrecosTopBar
import app.trecos.ui.text.SafeText
import java.util.Currency
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Picks the display currency. Choosing a different one first warns that
 * prices are relabelled, not converted; cancelling keeps the old one.
 *
 * @param onBack leaves the screen.
 */
@Composable
fun CurrencyScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val language = AppLanguage.current()
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    val all = remember(language) {
        Currency.getAvailableCurrencies().map { it.currencyCode to currencyLabel(it.currencyCode, language) }.sortedBy { it.first }
    }
    val shown = remember(query, all) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.second.contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.setting_currency), onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.currency_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("currency_search"),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = BottomBarClearance)) {
            items(shown, key = { it.first }) { (code, label) ->
                val selected = code == state?.currency
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { if (!selected) pending = code }
                        .padding(horizontal = 16.dp)
                        .testTag("currency_$code"),
                ) {
                    SafeText(label, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    pending?.let { code ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.currency_warning_title, code)) },
            text = { Text(stringResource(R.string.currency_warning), modifier = Modifier.testTag("currency_warning")) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setCurrency(code)
                    pending = null
                    onBack()
                }, modifier = Modifier.testTag("confirm_currency")) { Text(stringResource(R.string.action_change)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }, modifier = Modifier.testTag("cancel_currency")) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * Picks up to three detailed-view extras, including the current house's
 * custom fields. With three chosen, the rest are disabled and a note explains the limit.
 *
 * @param onBack leaves the screen.
 */
@Composable
fun ExtrasScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val app = appContainer()
    val current = state
    val houseId = current?.house?.id
    val fields by remember(houseId) { houseId?.let { app.database.fields().observeHouseFields(it) } ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.setting_extras), onBack = onBack)
        if (current == null) return@Column
        val full = current.extras.size >= DetailExtras.MAX
        Text(
            stringResource(R.string.extras_limit),
            color = if (full) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("extras_limit"),
        )
        val options = DetailExtras.builtIn.map { it to (builtInExtraLabel(it) ?: it) } +
            fields.map { DetailExtras.CUSTOM_PREFIX + it.id to it.name }
        LazyColumn(contentPadding = PaddingValues(bottom = BottomBarClearance)) {
            items(options, key = { it.first }) { (key, label) ->
                val checked = key in current.extras
                val enabled = checked || !full
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(enabled = enabled) { vm.toggleExtra(key) }
                        .padding(horizontal = 8.dp)
                        .testTag("extra_$key"),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                    SafeText(
                        label, maxLines = 1, modifier = Modifier.padding(start = 8.dp),
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A house's house-wide custom fields: add, rename (keeping values) and
 * delete (after confirming how many values go).
 *
 * @param houseId the house.
 * @param onBack leaves the screen.
 */
@Composable
fun HouseFieldsScreen(houseId: String, onBack: () -> Unit) {
    val app = appContainer()
    val scope = rememberCoroutineScope()
    val fields by remember(houseId) { app.database.fields().observeHouseFields(houseId) }.collectAsStateWithLifecycle(emptyList())
    val house by remember(houseId) { app.database.houses().observe(houseId) }.collectAsStateWithLifecycle(null)
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Pair<FieldDef, Int>?>(null) }
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(FieldType.Text) }
    var newUnit by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TrecosTopBar(title = stringResource(R.string.settings_fields), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomBarClearance), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Text(
                    stringResource(R.string.fields_intro, house?.name.orEmpty()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (fields.isEmpty()) item { Text(stringResource(R.string.no_fields), modifier = Modifier.testTag("no_fields")) }
            items(fields, key = { it.id }) { field ->
                if (renaming == field.id) {
                    var name by remember(field.id) { mutableStateOf(field.name) }
                    var unit by remember(field.id) { mutableStateOf(field.unit.orEmpty()) }
                    Column {
                        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.field_name)) }, modifier = Modifier.fillMaxWidth().testTag("rename_field"))
                        if (field.fieldType == FieldType.Number) {
                            OutlinedTextField(unit, { unit = it }, singleLine = true, label = { Text(stringResource(R.string.field_unit)) }, modifier = Modifier.fillMaxWidth().testTag("rename_unit"))
                        }
                        Row {
                            TextButton(onClick = {
                                scope.launch { app.fields.rename(field, name.trim(), unit) }
                                renaming = null
                            }, enabled = name.isNotBlank(), modifier = Modifier.testTag("confirm_rename")) { Text(stringResource(R.string.action_save)) }
                            TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("field_row_${field.name}")) {
                        Column(Modifier.weight(1f)) {
                            SafeText(field.name, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                typeLabel(field.fieldType) + (field.unit?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { renaming = field.id }, modifier = Modifier.testTag("rename_${field.name}")) { Text(stringResource(R.string.action_rename)) }
                        TextButton(onClick = {
                            scope.launch { deleting = field to app.fields.valueCount(field.id) }
                        }, modifier = Modifier.testTag("delete_${field.name}")) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.add_field), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                            nameError = false
                        },
                        label = { Text(stringResource(R.string.field_name)) },
                        isError = nameError,
                        supportingText = if (nameError) ({ Text(stringResource(R.string.error_field_name)) }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_field_name"),
                    )
                    TypePicker(newType) { newType = it }
                    if (newType == FieldType.Number) {
                        OutlinedTextField(newUnit, { newUnit = it }, singleLine = true, label = { Text(stringResource(R.string.field_unit)) }, modifier = Modifier.fillMaxWidth().testTag("new_field_unit"))
                    }
                    Button(onClick = {
                        val name = newName.trim()
                        if (name.isEmpty()) {
                            nameError = true
                        } else {
                            val type = newType
                            val unit = newUnit
                            scope.launch { app.fields.addHouseField(houseId, name, type, unit) }
                            newName = ""
                            newUnit = ""
                            newType = FieldType.Text
                        }
                    }, modifier = Modifier.testTag("add_field")) { Text(stringResource(R.string.add_field)) }
                }
            }
        }
    }
    deleting?.let { (field, count) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = {
                Text(
                    if (count == 0) stringResource(R.string.delete_field_unused, field.name)
                    else pluralStringResource(R.plurals.delete_field_used, count, count, field.name),
                    modifier = Modifier.testTag("delete_field_message"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.fields.delete(field.id) }
                    deleting = null
                }, modifier = Modifier.testTag("confirm_delete_field")) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }, modifier = Modifier.testTag("cancel_delete_field")) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * @param type a field type.
 * @return its label.
 */
@Composable
fun typeLabel(type: FieldType): String = stringResource(
    when (type) {
        FieldType.Text -> R.string.type_text
        FieldType.Number -> R.string.type_number
        FieldType.Date -> R.string.type_date
        FieldType.YesNo -> R.string.type_yes_no
    },
)

/**
 * A button showing the chosen field type, opening a menu of the four types.
 *
 * @param selected the chosen type.
 * @param onSelect called with the new type.
 */
@Composable
fun TypePicker(selected: FieldType, onSelect: (FieldType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("field_type")) {
            Text("${stringResource(R.string.field_type)}: ${typeLabel(selected)}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FieldType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(typeLabel(type)) },
                    onClick = {
                        open = false
                        onSelect(type)
                    },
                    modifier = Modifier.testTag("type_$type"),
                )
            }
        }
    }
}
