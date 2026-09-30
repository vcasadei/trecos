package app.trecos.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.trecos.R
import app.trecos.data.FieldType
import app.trecos.places.CustomFields
import app.trecos.places.FieldDraft
import app.trecos.places.FieldError
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.settings.TypePicker
import app.trecos.ui.text.SafeText

/**
 * The item form's custom fields (under "More fields"): the house's fields,
 * the item's own, and a small form to add a field to this item only.
 *
 * @param fields the fields with their inputs.
 * @param errors errors by field index.
 * @param onChange changes a field's input.
 * @param onRemove removes an item field, or clears a house field.
 * @param onAdd adds a field to this item.
 */
@Composable
fun CustomFieldsEditor(
    fields: List<FieldDraft>,
    errors: Map<Int, FieldError>,
    onChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: (String, FieldType, String?) -> Unit,
) {
    val language = AppLanguage.current()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.forEachIndexed { index, field ->
            val label = field.name + (field.unit?.let { " ($it)" } ?: "")
            if (field.type == FieldType.YesNo) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SafeText(label, maxLines = 2, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    listOf(CustomFields.YES to R.string.answer_yes, CustomFields.NO to R.string.answer_no).forEach { (value, text) ->
                        FilterChip(
                            selected = field.input == value,
                            onClick = { onChange(index, if (field.input == value) "" else value) },
                            label = { Text(stringResource(text)) },
                            modifier = Modifier.testTag("custom_${field.name}_$value"),
                        )
                    }
                }
            } else {
                val error = when (errors[index]) {
                    FieldError.NumberInvalid -> stringResource(R.string.error_number)
                    FieldError.DateInvalid -> stringResource(R.string.error_date, CustomFields.datePattern(language).lowercase())
                    else -> null
                }
                OutlinedTextField(
                    value = field.input,
                    onValueChange = { onChange(index, it) },
                    label = { Text(label) },
                    placeholder = if (field.type == FieldType.Date) ({ Text(CustomFields.datePattern(language).lowercase()) }) else null,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, modifier = Modifier.testTag("error_custom_${field.name}")) } },
                    singleLine = field.type != FieldType.Text,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field.type) {
                            FieldType.Number -> KeyboardType.Decimal
                            FieldType.Date -> KeyboardType.Number
                            else -> KeyboardType.Text
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("custom_${field.name}"),
                )
            }
            if (!field.houseWide) {
                TextButton(onClick = { onRemove(index) }, modifier = Modifier.testTag("remove_custom_${field.name}")) { Text(stringResource(R.string.remove_field)) }
            }
        }
        AddItemField(onAdd)
    }
}

/**
 * "Add a field to this item", opening a name, type and unit form inline.
 *
 * @param onAdd adds the field.
 */
@Composable
private fun AddItemField(onAdd: (String, FieldType, String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(FieldType.Text) }
    var unit by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    if (!open) {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("add_item_field")) { Text(stringResource(R.string.add_item_field)) }
        return
    }
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                nameError = false
            },
            label = { Text(stringResource(R.string.field_name)) },
            isError = nameError,
            supportingText = if (nameError) ({ Text(stringResource(R.string.error_field_name)) }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("item_field_name"),
        )
        TypePicker(type) { type = it }
        if (type == FieldType.Number) {
            OutlinedTextField(unit, { unit = it }, singleLine = true, label = { Text(stringResource(R.string.field_unit)) }, modifier = Modifier.fillMaxWidth().testTag("item_field_unit"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    nameError = true
                } else {
                    onAdd(trimmed, type, unit.takeIf { type == FieldType.Number })
                    open = false
                    name = ""
                    unit = ""
                    type = FieldType.Text
                }
            }, modifier = Modifier.testTag("confirm_item_field")) { Text(stringResource(R.string.action_add)) }
            TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
