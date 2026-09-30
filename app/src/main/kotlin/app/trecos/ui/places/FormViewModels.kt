package app.trecos.ui.places

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trecos.AppContainer
import app.trecos.data.Container
import app.trecos.data.House
import app.trecos.data.Item
import app.trecos.places.FieldError
import app.trecos.places.Money
import app.trecos.places.Validation
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.theme.PaletteColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Edits a new or existing house.
 *
 * @param app the app's container.
 * @param houseId the house to edit, or `null` to create one.
 */
class HouseFormViewModel(private val app: AppContainer, private val houseId: String?) : ViewModel() {
    private var existing: House? = null

    /** The typed name. */
    var name by mutableStateOf("")

    /** The typed address. */
    var address by mutableStateOf("")

    /** The typed description. */
    var description by mutableStateOf("")

    /** The chosen icon key. */
    var icon by mutableStateOf(PlaceIcons.defaultHouse)

    /** The chosen colour key, or `null` for none. */
    var colour by mutableStateOf<String?>(null)

    /** The name error, if the last save was rejected. */
    var nameError by mutableStateOf<FieldError?>(null)
        private set

    /** Whether the form has its initial values. */
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val houses = app.database.houses().observeAll().first()
            existing = houseId?.let { app.database.houses().get(it) }
            existing?.let {
                name = it.name
                address = it.address.orEmpty()
                description = it.description.orEmpty()
                icon = it.icon
                colour = it.colorKey
            } ?: run {
                val used = houses.mapNotNull { it.colorKey }.toSet()
                colour = (PaletteColor.entries.firstOrNull { it.key !in used } ?: PaletteColor.entries.first()).key
            }
            loaded = true
        }
    }

    /**
     * Validates and saves the house.
     *
     * @param onSaved called with the house id after a successful save.
     */
    fun save(onSaved: (String) -> Unit) {
        val validName = Validation.name(name)
        nameError = if (validName == null) FieldError.NameRequired else null
        if (validName == null) return
        viewModelScope.launch {
            val now = app.clock()
            val old = existing
            val house = House(
                id = old?.id ?: app.newId(),
                name = validName,
                address = Validation.optional(address),
                description = Validation.optional(description),
                icon = icon,
                colorKey = colour,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            )
            if (old == null) {
                app.database.houses().insert(house)
                app.preferences.setLastHouse(house.id)
            } else {
                app.database.houses().update(house)
            }
            onSaved(house.id)
        }
    }
}

/**
 * Edits a new or existing container.
 *
 * @param app the app's container.
 * @param houseId the house of a new container (ignored when editing).
 * @param parentId the parent of a new container, or `null` for the top level (ignored when editing).
 * @param containerId the container to edit, or `null` to create one.
 */
class ContainerFormViewModel(
    private val app: AppContainer,
    private val houseId: String,
    private val parentId: String?,
    private val containerId: String?,
) : ViewModel() {
    private var existing: Container? = null

    /** The typed name. */
    var name by mutableStateOf("")

    /** The typed description. */
    var description by mutableStateOf("")

    /** The typed QR code. */
    var qrCode by mutableStateOf("")

    /** The typed manual value. */
    var valueOverride by mutableStateOf("")

    /** The chosen icon key. */
    var icon by mutableStateOf(PlaceIcons.defaultContainer)

    /** The chosen colour key, or `null` to inherit. */
    var colour by mutableStateOf<String?>(null)

    /** Field errors of the last rejected save. */
    var errors by mutableStateOf<Set<FieldError>>(emptySet())
        private set

    init {
        if (containerId != null) {
            viewModelScope.launch {
                existing = app.database.containers().get(containerId)
                val currency = app.preferences.currency.first()
                existing?.let {
                    name = it.name
                    description = it.description.orEmpty()
                    qrCode = it.qrCode.orEmpty()
                    icon = it.icon
                    colour = it.colorKey
                    valueOverride = it.valueOverride?.let { v -> Money.formatInput(v, currency, AppLanguage.current()) }.orEmpty()
                }
            }
        }
    }

    /**
     * Validates and saves the container.
     *
     * @param onSaved called after a successful save.
     */
    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val old = existing
            val house = old?.houseId ?: houseId
            val currency = app.preferences.currency.first()
            val validName = Validation.name(name)
            val qr = Validation.optional(qrCode)
            val override = Validation.optional(valueOverride)?.let { Money.parse(it, currency, AppLanguage.current()) ?: -1L }
            val found = buildSet {
                if (validName == null) add(FieldError.NameRequired)
                if (override == -1L) add(FieldError.PriceInvalid)
                if (qr != null && app.database.qr().countUses(house, qr, old?.id ?: "") > 0) add(FieldError.QrInUse)
            }
            errors = found
            if (found.isNotEmpty() || validName == null) return@launch
            val now = app.clock()
            val container = Container(
                id = old?.id ?: app.newId(),
                houseId = house,
                parentId = old?.parentId ?: parentId,
                name = validName,
                description = Validation.optional(description),
                qrCode = qr,
                icon = icon,
                colorKey = colour,
                valueOverride = override,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            )
            if (old == null) app.database.containers().insert(container) else app.database.containers().update(container)
            onSaved()
        }
    }
}

/**
 * Edits a new or existing item. "Save + new" keeps the location.
 *
 * @param app the app's container.
 * @param houseId the house of a new item (ignored when editing).
 * @param containerId the container of a new item, or `null` for the top level (ignored when editing).
 * @param itemId the item to edit, or `null` to create one.
 */
class ItemFormViewModel(
    private val app: AppContainer,
    private val houseId: String,
    private val containerId: String?,
    private val itemId: String?,
) : ViewModel() {
    private var existing: Item? = null

    /** The typed name. */
    var name by mutableStateOf("")

    /** The typed quantity; starts at 1. */
    var quantity by mutableStateOf("1")

    /** The typed unit price. */
    var unitPrice by mutableStateOf("")

    /** The typed brand. */
    var brand by mutableStateOf("")

    /** The typed model. */
    var model by mutableStateOf("")

    /** The typed serial number. */
    var serial by mutableStateOf("")

    /** The typed QR code. */
    var qrCode by mutableStateOf("")

    /** The typed description. */
    var description by mutableStateOf("")

    /** Whether "More fields" is open. */
    var moreFields by mutableStateOf(false)

    /** Field errors of the last rejected save. */
    var errors by mutableStateOf<Set<FieldError>>(emptySet())
        private set

    /** Where the next saved item goes; kept by "Save + new". */
    var location: Pair<String, String?> = houseId to containerId
        private set

    init {
        if (itemId != null) {
            viewModelScope.launch {
                existing = app.database.items().get(itemId)
                val currency = app.preferences.currency.first()
                existing?.let {
                    name = it.name
                    quantity = it.quantity.toString()
                    unitPrice = it.unitPrice?.let { p -> Money.formatInput(p, currency, AppLanguage.current()) }.orEmpty()
                    brand = it.brand.orEmpty()
                    model = it.model.orEmpty()
                    serial = it.serial.orEmpty()
                    qrCode = it.qrCode.orEmpty()
                    description = it.description.orEmpty()
                    moreFields = listOf(brand, model, serial, qrCode, description).any(String::isNotEmpty)
                    location = it.houseId to it.containerId
                }
            }
        }
    }

    /**
     * Validates and saves the item.
     *
     * @param andNew whether to open an empty form in the same location afterwards.
     * @param onSaved called after a successful save when [andNew] is `false`.
     */
    fun save(andNew: Boolean, onSaved: () -> Unit) {
        viewModelScope.launch {
            val old = existing
            val currency = app.preferences.currency.first()
            val validName = Validation.name(name)
            val validQuantity = Validation.quantity(quantity)
            val price = Validation.optional(unitPrice)?.let { Money.parse(it, currency, AppLanguage.current()) ?: -1L }
            val qr = Validation.optional(qrCode)
            val found = buildSet {
                if (validName == null) add(FieldError.NameRequired)
                if (validQuantity == null) add(FieldError.QuantityInvalid)
                if (price == -1L) add(FieldError.PriceInvalid)
                if (qr != null && app.database.qr().countUses(location.first, qr, old?.id ?: "") > 0) add(FieldError.QrInUse)
            }
            errors = found
            if (found.isNotEmpty() || validName == null || validQuantity == null) return@launch
            val now = app.clock()
            val item = Item(
                id = old?.id ?: app.newId(),
                houseId = location.first,
                containerId = location.second,
                name = validName,
                quantity = validQuantity,
                unitPrice = price,
                brand = Validation.optional(brand),
                model = Validation.optional(model),
                serial = Validation.optional(serial),
                qrCode = qr,
                description = Validation.optional(description),
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            )
            if (old == null) app.database.items().insert(item) else app.database.items().update(item)
            if (andNew && old == null) {
                name = ""
                quantity = "1"
                unitPrice = ""
                brand = ""
                model = ""
                serial = ""
                qrCode = ""
                description = ""
            } else {
                onSaved()
            }
        }
    }
}
