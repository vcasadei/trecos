package app.trecos.ui.places

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.trecos.R
import app.trecos.places.FieldError
import app.trecos.data.AddFlow
import app.trecos.data.QrHolder
import app.trecos.ui.appContainer
import app.trecos.ui.appViewModel
import app.trecos.ui.shell.TrecosTopBar

/**
 * Returns the test tag of a form field.
 *
 * @param name the field's short name, such as `name`.
 * @return the tag, such as `field_name`.
 */
fun fieldTag(name: String): String = "field_$name"

/**
 * The frame of every form: a top bar with a back arrow, scrolling fields, and
 * the save buttons at the bottom.
 *
 * @param title the form's title.
 * @param onBack leaves without saving.
 * @param buttons the save buttons.
 * @param fields the form's fields.
 */
@Composable
private fun FormFrame(title: String, onBack: () -> Unit, buttons: @Composable () -> Unit, fields: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        TrecosTopBar(title = title, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = fields,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End),
        ) { buttons() }
    }
}

/**
 * A single-line text field with an optional error below it.
 *
 * @param label the field label.
 * @param value the current text.
 * @param onValueChange called as the user types.
 * @param tag the field's short name, for its test tag.
 * @param error the error message resource, or `null`.
 * @param keyboard the keyboard type.
 * @param singleLine whether the field is one line.
 * @param focus lets the form move the cursor into this field.
 */
@Composable
private fun Field(
    label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    error: Int? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    focus: FocusRequester? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it), modifier = Modifier.testTag("error_$tag")) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 5,
        modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier).testTag(fieldTag(tag)),
    )
}

/**
 * The QR code field: its error names the holder of a taken code, with a
 * button to open it.
 *
 * @param value the typed code.
 * @param onValueChange called as the user types.
 * @param errors the form's field errors.
 * @param holder who holds a taken code, or `null`.
 * @param onOpenHolder opens the holder.
 * @param onScanned receives a scanned code.
 */
@Composable
private fun QrField(
    value: String,
    onValueChange: (String) -> Unit,
    errors: Set<FieldError>,
    holder: QrHolder?,
    onOpenHolder: (QrHolder) -> Unit,
    onScanned: (String) -> Unit,
) {
    val app = appContainer()
    val scanner = LocalQrScanner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val message = when {
        FieldError.QrTooLong in errors -> stringResource(R.string.error_qr_too_long)
        FieldError.QrInUse in errors && holder != null -> stringResource(R.string.error_qr_in_use_by, holder.name)
        FieldError.QrInUse in errors -> stringResource(R.string.error_qr_in_use)
        else -> null
    }
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.field_qr)) },
            isError = message != null,
            supportingText = message?.let { { Text(it, modifier = Modifier.testTag("error_qr")) } },
            trailingIcon = {
                IconButton(
                    onClick = { scope.launch { scanForForm(scanner, context, app)?.let(onScanned) } },
                    modifier = Modifier.testTag("form_scan"),
                ) { Icon(painterResource(R.drawable.ic_qr), contentDescription = stringResource(R.string.scan_label)) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(fieldTag("qr")),
        )
        if (holder != null && FieldError.QrInUse in errors) {
            TextButton(onClick = { onOpenHolder(holder) }, modifier = Modifier.testTag("open_holder")) { Text(stringResource(R.string.open_holder)) }
        }
    }
}

/**
 * A label above a picker.
 *
 * @param label the label resource.
 */
@Composable
private fun PickerLabel(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The new or edit house form.
 *
 * @param houseId the house to edit, or `null` for a new one.
 * @param onDone leaves the form; called with the house id after saving, or `null` when cancelled.
 */
@Composable
fun HouseFormScreen(houseId: String?, onDone: (String?) -> Unit) {
    val vm = appViewModel(key = "houseForm/$houseId") { HouseFormViewModel(it, houseId) }
    FormFrame(
        title = stringResource(if (houseId == null) R.string.new_house else R.string.edit_house),
        onBack = { onDone(null) },
        buttons = { Button(onClick = { vm.save(onDone) }, modifier = Modifier.testTag("save")) { Text(stringResource(R.string.action_save)) } },
    ) {
        PhotoEditor(vm.photos.photos, vm.photos::import, vm.photos::setMain, vm.photos::remove, vm.photos::move)
        Field(R.string.field_name, vm.name, { vm.name = it }, "name", R.string.error_name_required.takeIf { vm.nameError == FieldError.NameRequired })
        Field(R.string.field_address, vm.address, { vm.address = it }, "address")
        Field(R.string.field_description, vm.description, { vm.description = it }, "description", singleLine = false)
        PickerLabel(R.string.field_icon)
        IconPicker(PlaceIcons.houses, vm.icon) { vm.icon = it }
        PickerLabel(R.string.field_colour)
        ColourPicker(vm.colour) { vm.colour = it }
        if (vm.otherHouses.isNotEmpty()) {
            PickerLabel(R.string.copy_from)
            CopyFromPicker(vm)
        }
    }
}

/**
 * Chooses a house to copy custom categories and tags from, or none.
 *
 * @param vm the house form's ViewModel.
 */
@Composable
private fun CopyFromPicker(vm: HouseFormViewModel) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("copy_from")) {
            Text(vm.otherHouses.firstOrNull { it.id == vm.copyFrom }?.name ?: stringResource(R.string.copy_none))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.copy_none)) }, onClick = {
                vm.copyFrom = null
                open = false
            })
            vm.otherHouses.forEach { house ->
                DropdownMenuItem(
                    text = { Text(house.name) },
                    onClick = {
                        vm.copyFrom = house.id
                        open = false
                    },
                    modifier = Modifier.testTag("copy_from_${house.id}"),
                )
            }
        }
    }
}

/**
 * The new or edit container form.
 *
 * @param houseId the house of a new container.
 * @param parentId the parent of a new container, or `null` for the top level.
 * @param containerId the container to edit, or `null` for a new one.
 * @param onDone leaves the form.
 * @param onOpenHolder opens the item or container holding a taken QR code.
 */
@Composable
fun ContainerFormScreen(
    houseId: String,
    parentId: String?,
    containerId: String?,
    onDone: () -> Unit,
    onOpenHolder: (QrHolder) -> Unit = {},
    scannedCode: String? = null,
) {
    val vm = appViewModel(key = "containerForm/$containerId/$parentId/$scannedCode") { ContainerFormViewModel(it, houseId, parentId, containerId, scannedCode) }
    FormFrame(
        title = stringResource(if (containerId == null) R.string.new_container else R.string.edit_container),
        onBack = onDone,
        buttons = { Button(onClick = { vm.save(onDone) }, modifier = Modifier.testTag("save")) { Text(stringResource(R.string.action_save)) } },
    ) {
        PhotoEditor(vm.photos.photos, vm.photos::import, vm.photos::setMain, vm.photos::remove, vm.photos::move)
        Field(R.string.field_name, vm.name, { vm.name = it }, "name", R.string.error_name_required.takeIf { FieldError.NameRequired in vm.errors })
        Field(R.string.field_description, vm.description, { vm.description = it }, "description", singleLine = false)
        QrField(vm.qrCode, { vm.qrCode = it }, vm.errors, vm.qrHolder, onOpenHolder) { code ->
            vm.qrCode = code
            if (vm.name.isBlank()) vm.name = code
        }
        Field(
            R.string.field_value_override, vm.valueOverride, { vm.valueOverride = it }, "override",
            R.string.error_price.takeIf { FieldError.PriceInvalid in vm.errors }, KeyboardType.Decimal,
        )
        PickerLabel(R.string.field_icon)
        IconPicker(PlaceIcons.containers, vm.icon) { vm.icon = it }
        PickerLabel(R.string.field_colour)
        ColourPicker(vm.colour) { vm.colour = it }
    }
}

/**
 * The new or edit item form: essentials first, the rest behind "More fields".
 *
 * @param houseId the house of a new item.
 * @param containerId the container of a new item, or `null` for the top level.
 * @param itemId the item to edit, or `null` for a new one.
 * @param onDone leaves the form.
 * @param onOpenHolder opens the item or container holding a taken QR code.
 */
@Composable
fun ItemFormScreen(
    houseId: String,
    containerId: String?,
    itemId: String?,
    onDone: () -> Unit,
    onOpenHolder: (QrHolder) -> Unit = {},
    scannedCode: String? = null,
) {
    val vm = appViewModel(key = "itemForm/$itemId/$containerId/$scannedCode") { ItemFormViewModel(it, houseId, containerId, itemId, scannedCode) }
    val addFlow by appContainer().preferences.addFlow.collectAsState(initial = null)
    var photoFirstPending by rememberSaveable { mutableStateOf(itemId == null) }
    var focusName by rememberSaveable { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(focusName) {
        if (focusName) {
            runCatching { nameFocus.requestFocus() }
            focusName = false
        }
    }
    FormFrame(
        title = stringResource(if (itemId == null) R.string.new_item else R.string.edit_item),
        onBack = onDone,
        buttons = {
            if (itemId == null) {
                OutlinedButton(onClick = { vm.save(andNew = true, onDone) }, modifier = Modifier.testTag("save_new")) {
                    Text(stringResource(R.string.action_save_new))
                }
            }
            Button(onClick = { vm.save(andNew = false, onDone) }, modifier = Modifier.testTag("save")) { Text(stringResource(R.string.action_save)) }
        },
    ) {
        PhotoEditor(
            photos = vm.photos.photos,
            onPicked = { uris ->
                vm.photos.import(uris)
                if (uris.isNotEmpty() || addFlow == AddFlow.PhotoFirst) focusName = true
            },
            onSetMain = vm.photos::setMain,
            onRemove = vm.photos::remove,
            onMove = vm.photos::move,
            requestAdd = photoFirstPending && addFlow == AddFlow.PhotoFirst,
            onAddRequested = { photoFirstPending = false },
        )
        Field(
            R.string.field_name, vm.name, { vm.name = it }, "name", R.string.error_name_required.takeIf { FieldError.NameRequired in vm.errors },
            focus = nameFocus,
        )
        CategoriesField(vm)
        Field(
            R.string.field_quantity, vm.quantity, { vm.quantity = it }, "quantity",
            R.string.error_quantity.takeIf { FieldError.QuantityInvalid in vm.errors }, KeyboardType.Number,
        )
        Field(
            R.string.field_unit_price, vm.unitPrice, { vm.unitPrice = it }, "price",
            R.string.error_price.takeIf { FieldError.PriceInvalid in vm.errors }, KeyboardType.Decimal,
        )
        TextButton(onClick = { vm.moreFields = !vm.moreFields }, modifier = Modifier.testTag("more_fields")) {
            Text(stringResource(if (vm.moreFields) R.string.fewer_fields else R.string.more_fields))
        }
        if (vm.moreFields) {
            Field(R.string.field_brand, vm.brand, { vm.brand = it }, "brand")
            Field(R.string.field_model, vm.model, { vm.model = it }, "model")
            Field(R.string.field_serial, vm.serial, { vm.serial = it }, "serial")
            QrField(vm.qrCode, { vm.qrCode = it }, vm.errors, vm.qrHolder, onOpenHolder) { code ->
            vm.qrCode = code
            if (vm.name.isBlank()) vm.name = code
        }
            Field(R.string.field_description, vm.description, { vm.description = it }, "description", singleLine = false)
            TagsField(vm)
        }
    }
}
