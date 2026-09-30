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
import app.trecos.data.Photo
import app.trecos.AppMessage
import app.trecos.R
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import app.trecos.data.ItemCategory
import app.trecos.data.QrHolder
import app.trecos.data.CustomCategory
import app.trecos.categories.CategoryCatalog
import app.trecos.categories.CategorySuggester
import app.trecos.categories.TagStore
import app.trecos.categories.TextNormalizer
import app.trecos.places.FieldError
import app.trecos.places.Money
import app.trecos.places.FieldDraft
import app.trecos.data.FieldType
import app.trecos.places.Validation
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.theme.PaletteColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Checks a typed QR code and, for a new record with the field left blank,
 * picks its name as the code when that is free (spec "QR code identity").
 *
 * @param app the app's container.
 * @param houseId the record's house.
 * @param typed what the user typed in the QR field.
 * @param name the record's validated name, or `null`.
 * @param exceptId the record being edited, or an empty string for a new one.
 * @param isNew whether the record is being created.
 * @return the code to save (or `null`), the error, and the holder of a taken code.
 */
internal suspend fun resolveQr(app: AppContainer, houseId: String, typed: String, name: String?, exceptId: String, isNew: Boolean): QrDecision {
    val code = Validation.optional(typed)
    if (code != null) {
        if (code.length > Validation.MAX_QR) return QrDecision(null, FieldError.QrTooLong, null)
        val holder = app.database.qr().holder(houseId, code, exceptId)
        return if (holder != null) QrDecision(null, FieldError.QrInUse, holder) else QrDecision(code, null, null)
    }
    if (!isNew || name == null || name.length > Validation.MAX_QR) return QrDecision(null, null, null)
    if (app.database.qr().countUses(houseId, name, exceptId) == 0) return QrDecision(name, null, null)
    app.messages.tryEmit(AppMessage(app.resources.getString(R.string.qr_auto_taken, name)))
    return QrDecision(null, null, null)
}

/**
 * The outcome of [resolveQr].
 *
 * @property code the code to save, or `null` for none.
 * @property error the QR field's error, or `null`.
 * @property holder who holds a taken code, or `null`.
 */
internal data class QrDecision(val code: String?, val error: FieldError?, val holder: QrHolder?)

/**
 * The photos of a form while it is being edited: imported at once, saved as
 * photo rows only when the form is saved.
 *
 * @param app the app's container.
 * @param scope the form's scope.
 */
class PhotoDraft(private val app: AppContainer, private val scope: CoroutineScope) {

    /** The photos' SHA-256s, main first. */
    var photos by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * Loads an existing owner's photos.
     *
     * @param ownerId the item, container or house.
     */
    suspend fun load(ownerId: String) {
        photos = app.database.photos().forOwner(ownerId).map { it.sha256 }
    }

    /**
     * Imports picked or captured images, up to the free slots. Images that
     * can't be decoded are reported; the others are added.
     *
     * @param uris the images.
     */
    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            var failed = 0
            for (uri in uris) {
                if (photos.size >= Photo.MAX_PER_OWNER) break
                val sha = app.photoStore.import(uri)
                if (sha == null) failed++ else if (sha !in photos) photos = photos + sha
            }
            app.cameraDir.listFiles()?.forEach { it.delete() }
            if (failed > 0) app.messages.tryEmit(AppMessage(app.resources.getQuantityString(R.plurals.photos_failed, failed, failed)))
        }
    }

    /** @param sha a photo to make the main one. */
    fun setMain(sha: String) {
        if (sha in photos) photos = listOf(sha) + (photos - sha)
    }

    /** @param sha a photo to remove. */
    fun remove(sha: String) {
        photos = photos - sha
    }

    /**
     * Moves a photo.
     *
     * @param from its position.
     * @param to its new position.
     */
    fun move(from: Int, to: Int) {
        if (from !in photos.indices || to !in photos.indices) return
        photos = photos.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Starts over with no photos, for "Save + new". */
    fun clear() {
        photos = emptyList()
    }

    /**
     * Saves the photos as the owner's photo rows.
     *
     * @param houseId the owner's house.
     * @param ownerType [Photo.OWNER_ITEM], [Photo.OWNER_CONTAINER] or [Photo.OWNER_HOUSE].
     * @param ownerId the owner.
     */
    suspend fun save(houseId: String, ownerType: String, ownerId: String) {
        val now = app.clock()
        app.database.photos().replaceFor(ownerId, photos.mapIndexed { i, sha -> Photo(app.newId(), houseId, ownerType, ownerId, sha, i, now) })
    }
}

/**
 * Edits a new or existing house.
 *
 * @param app the app's container.
 * @param houseId the house to edit, or `null` to create one.
 */
class HouseFormViewModel(private val app: AppContainer, private val houseId: String?) : ViewModel() {
    private var existing: House? = null

    /** The house's photos. */
    val photos = PhotoDraft(app, viewModelScope)

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

    /** Other houses a new house can copy custom categories and tags from. */
    var otherHouses by mutableStateOf<List<House>>(emptyList())
        private set

    /** The house to copy from, or `null` for built-in categories only. */
    var copyFrom by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            val houses = app.database.houses().observeAll().first()
            existing = houseId?.let { app.database.houses().get(it) }
            if (houseId == null) otherHouses = houses
            existing?.let {
                photos.load(it.id)
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
     * Copies a house's custom categories (keeping subcategories under their
     * copied parents), tags and house-wide fields into another house. Items are never copied.
     *
     * @param from the house to copy from.
     * @param to the new house.
     */
    private suspend fun copyCategoriesAndTags(from: String, to: String) {
        val now = app.clock()
        val categories = app.database.categories().custom(from)
        val newIds = categories.associate { it.id to app.newId() }
        categories.sortedBy { it.parentId in newIds }.forEach { category ->
            app.database.categories().insertCustom(
                category.copy(id = newIds.getValue(category.id), houseId = to, parentId = category.parentId?.let { newIds[it] ?: it }, createdAt = now, updatedAt = now),
            )
        }
        app.database.tags().all(from).forEach { tag ->
            app.database.tags().insert(tag.copy(id = app.newId(), houseId = to, createdAt = now, updatedAt = now))
        }
        app.database.fields().houseFields(from).forEach { field ->
            app.database.fields().insertDef(field.copy(id = app.newId(), houseId = to, createdAt = now, updatedAt = now))
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
                copyFrom?.let { copyCategoriesAndTags(from = it, to = house.id) }
                app.preferences.setLastHouse(house.id)
            } else {
                app.database.houses().update(house)
            }
            photos.save(house.id, Photo.OWNER_HOUSE, house.id)
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
 * @param scannedCode a scanned code that nothing has, filled in as the new container's code and name.
 */
class ContainerFormViewModel(
    private val app: AppContainer,
    private val houseId: String,
    private val parentId: String?,
    private val containerId: String?,
    scannedCode: String? = null,
) : ViewModel() {
    private var existing: Container? = null

    /** The container's photos. */
    val photos = PhotoDraft(app, viewModelScope)

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

    /**
     * Fills in a scanned code, and the name too when it is still empty.
     *
     * @param code the scanned code.
     */
    fun fillFromScan(code: String) {
        qrCode = code
        if (name.isBlank()) name = code
    }

    /** Who holds the code the user typed, when it is taken. */
    var qrHolder by mutableStateOf<QrHolder?>(null)
        private set

    init {
        if (containerId == null && scannedCode != null) {
            qrCode = scannedCode
            name = scannedCode
        }
        if (containerId != null) {
            viewModelScope.launch {
                existing = app.database.containers().get(containerId)
                val currency = app.preferences.currency.first()
                existing?.let {
                    photos.load(it.id)
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
            val override = Validation.optional(valueOverride)?.let { Money.parse(it, currency, AppLanguage.current()) ?: -1L }
            val decision = if (validName == null) QrDecision(null, null, null) else resolveQr(app, house, qrCode, validName, old?.id ?: "", old == null)
            val qr = decision.code
            qrHolder = decision.holder
            val found = buildSet {
                if (validName == null) add(FieldError.NameRequired)
                if (override == -1L) add(FieldError.PriceInvalid)
                decision.error?.let { add(it) }
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
            photos.save(container.houseId, Photo.OWNER_CONTAINER, container.id)
            onSaved()
        }
    }
}

/**
 * Edits a new or existing item, with its categories and tags. "Save + new"
 * keeps the location and the categories.
 *
 * @param app the app's container.
 * @param houseId the house of a new item (ignored when editing).
 * @param containerId the container of a new item, or `null` for the top level (ignored when editing).
 * @param itemId the item to edit, or `null` to create one.
 * @param scannedCode a scanned code that nothing has, filled in as the new item's code and name.
 */
class ItemFormViewModel(
    private val app: AppContainer,
    private val houseId: String,
    private val containerId: String?,
    private val itemId: String?,
    scannedCode: String? = null,
) : ViewModel() {
    private var existing: Item? = null
    private val tagStore = TagStore(app.database.tags(), app.clock, app.newId)

    /** The item's photos. */
    val photos = PhotoDraft(app, viewModelScope)
    private var suggestionJob: Job? = null
    private var completionJob: Job? = null

    /** The typed name; changing it refreshes the suggestions. */
    var name: String
        get() = nameState
        set(value) {
            nameState = value
            refreshSuggestions()
        }
    private var nameState by mutableStateOf("")

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

    /** The typed description; changing it refreshes the suggestions. */
    var description: String
        get() = descriptionState
        set(value) {
            descriptionState = value
            refreshSuggestions()
        }
    private var descriptionState by mutableStateOf("")

    /** Whether "More fields" is open. */
    var moreFields by mutableStateOf(false)

    /** The item's categories, main first. */
    var categories by mutableStateOf<List<String>>(emptyList())
        private set

    /** The item's tag names. */
    var tags by mutableStateOf<List<String>>(emptyList())
        private set

    /** The tag being typed; changing it refreshes the completions. */
    var tagInput: String
        get() = tagInputState
        set(value) {
            tagInputState = value
            refreshCompletions()
        }
    private var tagInputState by mutableStateOf("")

    /** Existing tags offered for [tagInput]. */
    var tagCompletions by mutableStateOf<List<String>>(emptyList())
        private set

    /** The house's categories: built-ins plus its custom ones. */
    var catalog by mutableStateOf(CategoryCatalog(app.builtInCategories, emptyList()))
        private set

    /** Up to three suggested categories not yet on the item. */
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    /** Field errors of the last rejected save. */
    var errors by mutableStateOf<Set<FieldError>>(emptySet())
        private set

    /**
     * Fills in a scanned code, and the name too when it is still empty.
     *
     * @param code the scanned code.
     */
    fun fillFromScan(code: String) {
        qrCode = code
        if (name.isBlank()) name = code
    }

    /** Who holds the code the user typed, when it is taken. */
    var qrHolder by mutableStateOf<QrHolder?>(null)
        private set

    /** The custom fields: the house's, then the item's own. */
    var fields by mutableStateOf<List<FieldDraft>>(emptyList())
        private set

    /** Errors of custom fields, by their index in [fields]. */
    var fieldErrors by mutableStateOf<Map<Int, FieldError>>(emptyMap())
        private set

    /**
     * Changes a custom field's input.
     *
     * @param index the field's index in [fields].
     * @param input the new input.
     */
    fun setField(index: Int, input: String) {
        fields = fields.mapIndexed { i, draft -> if (i == index) draft.copy(input = input) else draft }
        fieldErrors = fieldErrors - index
    }

    /**
     * Adds a field to this item only.
     *
     * @param name the field's name, not empty.
     * @param type its type.
     * @param unit a Number field's unit, or `null`.
     */
    fun addItemField(name: String, type: FieldType, unit: String?) {
        fields = fields + FieldDraft(defId = null, name = name, type = type, unit = unit?.trim()?.ifEmpty { null })
    }

    /**
     * Removes one of this item's own fields (house-wide fields stay, and are cleared instead).
     *
     * @param index the field's index in [fields].
     */
    fun removeField(index: Int) {
        val draft = fields.getOrNull(index) ?: return
        fields = if (draft.houseWide) fields.mapIndexed { i, d -> if (i == index) d.copy(input = "") else d } else fields.filterIndexed { i, _ -> i != index }
        fieldErrors = emptyMap()
    }

    /** Where the next saved item goes; kept by "Save + new". */
    var location: Pair<String, String?> = houseId to containerId
        private set

    init {
        if (itemId == null && scannedCode != null) {
            qrCode = scannedCode
            nameState = scannedCode
            moreFields = true
        }
        viewModelScope.launch {
            if (itemId != null) {
                existing = app.database.items().get(itemId)
                val currency = app.preferences.currency.first()
                existing?.let {
                    photos.load(it.id)
                    nameState = it.name
                    quantity = it.quantity.toString()
                    unitPrice = it.unitPrice?.let { p -> Money.formatInput(p, currency, AppLanguage.current()) }.orEmpty()
                    brand = it.brand.orEmpty()
                    model = it.model.orEmpty()
                    serial = it.serial.orEmpty()
                    qrCode = it.qrCode.orEmpty()
                    descriptionState = it.description.orEmpty()
                    location = it.houseId to it.containerId
                    categories = app.database.categories().forItem(it.id)
                    tags = app.database.tags().forItem(it.id).map { tag -> tag.name }
                    // Only ever opens "More fields": the user may already have opened it while this loaded.
                    if (listOf(brand, model, serial, qrCode, descriptionState).any(String::isNotEmpty) || tags.isNotEmpty()) moreFields = true
                }
            }
            fields = app.fields.drafts(location.first, existing?.id, AppLanguage.current())
            if (fields.any { it.input.isNotEmpty() }) moreFields = true
            app.database.categories().observeCustom(location.first).collect { custom ->
                catalog = CategoryCatalog(app.builtInCategories, custom)
                categories = categories.filter { catalog[it] != null }
            }
        }
    }

    /**
     * Adds a category, or removes it if the item already has it. The first
     * category added becomes the main one.
     *
     * @param id the category id or key.
     */
    fun toggleCategory(id: String) {
        categories = if (id in categories) categories - id else categories + id
        refreshSuggestions()
    }

    /**
     * Makes an assigned category the main one.
     *
     * @param id the category id or key.
     */
    fun setMain(id: String) {
        if (id in categories) categories = listOf(id) + (categories - id)
    }

    /**
     * Creates a custom category in the item's house and assigns it.
     *
     * @param name the name, shown as typed.
     * @param parentId a top-level category to put it under, or `null` for a new top level.
     * @param icon an icon key, or `null` for the empty default.
     */
    fun createCategory(name: String, parentId: String?, icon: String?) {
        val trimmed = name.trim().ifEmpty { return }
        viewModelScope.launch {
            val now = app.clock()
            val category = CustomCategory(app.newId(), location.first, parentId, trimmed, icon, now, now)
            app.database.categories().insertCustom(category)
            categories = categories + category.id
        }
    }

    /**
     * @param id a custom category id.
     * @return how many items have it, for the delete confirmation.
     */
    suspend fun usage(id: String): Int = app.database.categories().usage(id)

    /**
     * Deletes a custom category everywhere; items keep their other categories.
     *
     * @param id the custom category id.
     */
    fun deleteCategory(id: String) {
        viewModelScope.launch {
            app.database.categories().deleteCustom(id)
            categories = categories - id
        }
    }

    /**
     * Adds a tag by name; an existing tag differing only in case or accents is reused on save.
     *
     * @param name the tag name.
     */
    fun addTag(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        val key = TextNormalizer.normalize(trimmed)
        if (tags.none { TextNormalizer.normalize(it) == key }) tags = tags + trimmed
        tagInput = ""
    }

    /**
     * @param name a tag name on the item.
     */
    fun removeTag(name: String) {
        tags = tags - name
    }

    private fun refreshSuggestions() {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(300)
            val text = "$nameState $descriptionState"
            val learned = CategorySuggester.learned(app.database.categories(), location.first, text)
            suggestions = app.suggester.suggest(text, catalog, categories, learned)
        }
    }

    private fun refreshCompletions() {
        completionJob?.cancel()
        completionJob = viewModelScope.launch {
            val onItem = tags.map(TextNormalizer::normalize).toSet()
            tagCompletions = tagStore.complete(location.first, tagInputState)
                .map { it.name }
                .filter { TextNormalizer.normalize(it) !in onItem }
        }
    }

    /**
     * Validates and saves the item with its categories and tags.
     *
     * @param andNew whether to open an empty form in the same location, keeping the categories.
     * @param onSaved called after a successful save when [andNew] is `false`.
     */
    fun save(andNew: Boolean, onSaved: () -> Unit) {
        viewModelScope.launch {
            // Everything is read when Save is tapped, before anything suspends: typing during
            // the save neither leaks into this item nor, with "Save + new", gets cleared.
            val saved = listOf(nameState, quantity, unitPrice, brand, model, serial, qrCode, descriptionState)
            val (typedName, typedQuantity, typedPrice, typedBrand, typedModel) = saved
            val (typedSerial, typedQr, typedDescription) = saved.drop(5)
            val savedCategories = categories
            val savedTags = tags
            val savedFields = fields
            val old = existing
            val currency = app.preferences.currency.first()
            val validName = Validation.name(typedName)
            val validQuantity = Validation.quantity(typedQuantity)
            val price = Validation.optional(typedPrice)?.let { Money.parse(it, currency, AppLanguage.current()) ?: -1L }
            val decision = if (validName == null) QrDecision(null, null, null) else resolveQr(app, location.first, typedQr, validName, old?.id ?: "", old == null)
            val qr = decision.code
            qrHolder = decision.holder
            val found = buildSet {
                if (validName == null) add(FieldError.NameRequired)
                if (validQuantity == null) add(FieldError.QuantityInvalid)
                if (price == -1L) add(FieldError.PriceInvalid)
                decision.error?.let { add(it) }
            }
            errors = found
            val language = AppLanguage.current()
            fieldErrors = app.fields.validate(savedFields, language)
            if (fieldErrors.isNotEmpty()) moreFields = true
            if (found.isNotEmpty() || fieldErrors.isNotEmpty() || validName == null || validQuantity == null) return@launch
            val now = app.clock()
            val item = Item(
                id = old?.id ?: app.newId(),
                houseId = location.first,
                containerId = location.second,
                name = validName,
                quantity = validQuantity,
                unitPrice = price,
                brand = Validation.optional(typedBrand),
                model = Validation.optional(typedModel),
                serial = Validation.optional(typedSerial),
                qrCode = qr,
                description = Validation.optional(typedDescription),
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            )
            if (old == null) app.database.items().insert(item) else app.database.items().update(item)
            app.database.categories().replaceForItem(
                item.id,
                savedCategories.mapIndexed { i, id -> ItemCategory(app.newId(), item.houseId, item.id, id, i, now) },
            )
            CategorySuggester.learn(app.database.categories(), item.houseId, "${item.name} ${item.description.orEmpty()}", savedCategories)
            tagStore.setForItem(item.houseId, item.id, savedTags)
            photos.save(item.houseId, Photo.OWNER_ITEM, item.id)
            app.fields.save(item.houseId, item.id, savedFields, language)
            if (andNew && old == null) {
                fields = app.fields.drafts(item.houseId, null, language)
                photos.clear()
                if (nameState == saved[0]) nameState = ""
                if (quantity == saved[1]) quantity = "1"
                if (unitPrice == saved[2]) unitPrice = ""
                if (brand == saved[3]) brand = ""
                if (model == saved[4]) model = ""
                if (serial == saved[5]) serial = ""
                if (qrCode == saved[6]) qrCode = ""
                if (descriptionState == saved[7]) descriptionState = ""
                if (tags == savedTags) tags = emptyList()
                suggestions = emptyList()
            } else {
                onSaved()
            }
        }
    }
}
