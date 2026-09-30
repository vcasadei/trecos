package app.trecos.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.trecos.R
import app.trecos.categories.Category
import app.trecos.categories.CategoryIcons
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.text.SafeText
import kotlinx.coroutines.launch

/**
 * Returns the test tag of a category row in the picker.
 *
 * @param id the category id or key.
 * @return the tag, such as `pick_cables.usb_c`.
 */
fun pickTag(id: String): String = "pick_$id"

/**
 * Returns the test tag of an assigned category chip on the form.
 *
 * @param id the category id or key.
 * @return the tag, such as `chosen_cables.usb_c`.
 */
fun chosenTag(id: String): String = "chosen_$id"

/**
 * Returns the test tag of a suggestion chip.
 *
 * @param id the category id or key.
 * @return the tag, such as `suggest_cables.hdmi`.
 */
fun suggestTag(id: String): String = "suggest_$id"

/**
 * The categories part of the item form: the assigned categories (main first),
 * an "Add category" button that opens the picker, and suggestion chips that
 * add a category only when tapped.
 *
 * @param vm the item form's ViewModel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesField(vm: ItemFormViewModel) {
    val language = AppLanguage.current()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.field_categories), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.categories.forEachIndexed { index, id ->
                val category = vm.catalog[id] ?: return@forEachIndexed
                InputChip(
                    selected = index == 0,
                    onClick = { pickerOpen = true },
                    label = {
                        SafeText(
                            (if (index == 0) "${stringResource(R.string.main_category)}: " else "") + vm.catalog.label(id, language).orEmpty(),
                            maxLines = 1,
                        )
                    },
                    leadingIcon = { Icon(painterResource(CategoryIcons.drawable(category.icon)), contentDescription = null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag(chosenTag(id)),
                )
            }
            AssistChip(
                onClick = { pickerOpen = true },
                label = { Text(stringResource(R.string.add_category)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("add_category"),
            )
        }
        val suggestions = vm.suggestions.filter { it !in vm.categories }
        if (suggestions.isNotEmpty()) {
            Text(stringResource(R.string.suggestions), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { id ->
                    SuggestionChip(
                        onClick = { vm.toggleCategory(id) },
                        label = { SafeText(vm.catalog.label(id, language).orEmpty(), maxLines = 1) },
                        modifier = Modifier.testTag(suggestTag(id)),
                    )
                }
            }
        }
    }
    if (pickerOpen) CategoryPicker(vm) { pickerOpen = false }
}

/**
 * The full-screen category picker: a search box, the assigned categories with
 * "Set as main", the category tree, and custom category actions.
 *
 * @param vm the item form's ViewModel.
 * @param onDone closes the picker.
 */
@Composable
private fun CategoryPicker(vm: ItemFormViewModel, onDone: () -> Unit) {
    val language = AppLanguage.current()
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Pair<Category, Int>?>(null) }
    val scope = rememberCoroutineScope()
    val matches = vm.catalog.search(query, language)

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(16.dp)
                .testTag("category_picker"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.field_categories), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDone, modifier = Modifier.testTag("picker_done")) { Text(stringResource(R.string.action_done)) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_categories)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("category_search"),
            )
            vm.categories.forEachIndexed { index, id ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("assigned_$id")) {
                    SafeText(
                        (if (index == 0) "★ " else "") + vm.catalog.label(id, language).orEmpty(),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (index > 0) {
                        TextButton(onClick = { vm.setMain(id) }, modifier = Modifier.testTag("set_main_$id")) { Text(stringResource(R.string.set_main)) }
                    }
                    TextButton(onClick = { vm.toggleCategory(id) }) { Text(stringResource(R.string.action_remove)) }
                }
            }
            if (creating) {
                NewCategoryForm(vm, onDone = { creating = false })
                return@Column
            }
            OutlinedButton(onClick = { creating = true }, modifier = Modifier.testTag("new_category")) { Text(stringResource(R.string.new_category)) }
            LazyColumn(Modifier.weight(1f)) {
                items(matches, key = { it.id }) { category ->
                    PickerRow(
                        category = category,
                        label = if (query.isBlank()) category.name(language) else vm.catalog.label(category.id, language).orEmpty(),
                        checked = category.id in vm.categories,
                        indent = category.parentId != null && query.isBlank(),
                        onToggle = { vm.toggleCategory(category.id) },
                        onDelete = if (category.editable) ({ scope.launch { deleting = category to vm.usage(category.id) } }) else null,
                    )
                }
            }
        }
    }
    deleting?.let { (category, count) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_category)) },
            text = { Text(pluralStringResource(R.plurals.delete_category_confirm, count, count)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(category.id)
                    deleting = null
                }, modifier = Modifier.testTag("confirm_delete_category")) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * One category in the picker, with a checkbox and, for custom ones, a delete menu.
 *
 * @param category the category.
 * @param label the text shown.
 * @param checked whether the item has it.
 * @param indent whether to indent it as a subcategory.
 * @param onToggle adds or removes it.
 * @param onDelete deletes a custom category, or `null` for built-ins, which offer no rename or delete.
 */
@Composable
private fun PickerRow(category: Category, label: String, checked: Boolean, indent: Boolean, onToggle: () -> Unit, onDelete: (() -> Unit)?) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = if (indent) 24.dp else 0.dp)
            .testTag(pickTag(category.id)),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Icon(painterResource(CategoryIcons.drawable(category.icon)), contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        SafeText(
            label,
            maxLines = 1,
            style = if (category.parentId == null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (onDelete != null) {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("category_menu_${category.id}")) {
                Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("delete_category_${category.id}"),
                )
            }
        }
    }
}

/**
 * Creates a custom category inside the picker: name, where it goes (under a
 * top level or its own top level) and an icon (empty by default).
 *
 * @param vm the item form's ViewModel.
 * @param onDone closes the form.
 */
@Composable
private fun NewCategoryForm(vm: ItemFormViewModel, onDone: () -> Unit) {
    val language = AppLanguage.current()
    var name by rememberSaveable { mutableStateOf("") }
    var parent by rememberSaveable { mutableStateOf<String?>(null) }
    var icon by rememberSaveable { mutableStateOf<String?>(null) }
    var parentMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("new_category_form")) {
        Text(stringResource(R.string.new_category), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("new_category_name"),
        )
        Text(stringResource(R.string.category_parent), style = MaterialTheme.typography.labelLarge)
        Column {
            OutlinedButton(onClick = { parentMenu = true }, modifier = Modifier.testTag("new_category_parent")) {
                SafeText(parent?.let { vm.catalog[it]?.name(language) } ?: stringResource(R.string.category_top_level), maxLines = 1)
            }
            DropdownMenu(expanded = parentMenu, onDismissRequest = { parentMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.category_top_level)) }, onClick = {
                    parent = null
                    parentMenu = false
                })
                vm.catalog.topLevel.forEach { top ->
                    DropdownMenuItem(
                        text = { Text(top.name(language)) },
                        onClick = {
                            parent = top.id
                            parentMenu = false
                        },
                        modifier = Modifier.testTag("parent_${top.id}"),
                    )
                }
            }
        }
        Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.labelLarge)
        IconPicker(
            icons = CategoryIcons.keys.map { PlaceIcon(it, CategoryIcons.drawable(it)) },
            selected = icon ?: CategoryIcons.EMPTY,
            onSelect = { icon = if (it == CategoryIcons.EMPTY) null else it },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) }
            TextButton(
                onClick = {
                    vm.createCategory(name, parent, icon)
                    onDone()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("create_category"),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

/**
 * The tags part of the item form: the item's tags as removable chips, a text
 * field, and existing tags offered as the user types.
 *
 * @param vm the item form's ViewModel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsField(vm: ItemFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.field_tags), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { vm.removeTag(tag) },
                    label = { SafeText(tag, maxLines = 1) },
                    trailingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_remove), Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tag_$tag"),
                )
            }
        }
        OutlinedTextField(
            value = vm.tagInput,
            onValueChange = { vm.tagInput = it },
            label = { Text(stringResource(R.string.add_tag)) },
            singleLine = true,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { vm.addTag(vm.tagInput) }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = { vm.addTag(vm.tagInput) }, modifier = Modifier.testTag("add_tag")) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_tag))
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(fieldTag("tag")),
        )
        vm.tagCompletions.forEach { completion ->
            Text(
                text = completion,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.addTag(completion) }
                    .padding(8.dp)
                    .testTag("complete_$completion"),
            )
        }
    }
}
