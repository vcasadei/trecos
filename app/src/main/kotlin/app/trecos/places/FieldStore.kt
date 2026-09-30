package app.trecos.places

import androidx.room.withTransaction
import app.trecos.data.FieldDef
import app.trecos.data.FieldType
import app.trecos.data.FieldValue
import app.trecos.data.TrecosDatabase
import app.trecos.ui.language.AppLanguage

/**
 * One custom field on an item form: a house-wide field, an existing per-item
 * field, or a new per-item field ([defId] `null`).
 *
 * @property defId the definition's id, or `null` for a field added on this form.
 * @property name the field's name.
 * @property type the field's type.
 * @property unit a Number field's unit, or `null`.
 * @property houseWide whether the field belongs to every item of the house.
 * @property input what the form's input shows: typed text, or [CustomFields.YES]/[CustomFields.NO].
 */
data class FieldDraft(
    val defId: String?,
    val name: String,
    val type: FieldType,
    val unit: String? = null,
    val houseWide: Boolean = false,
    val input: String = "",
)

/**
 * Custom field definitions and item values (custom-fields spec).
 *
 * @param db the database.
 * @param clock the current time, epoch milliseconds.
 * @param newId makes a new random id.
 */
class FieldStore(private val db: TrecosDatabase, private val clock: () -> Long, private val newId: () -> String) {
    private val dao = db.fields()

    /**
     * Adds a house-wide field at the end.
     *
     * @param houseId the house.
     * @param name the field's name, already trimmed and not empty.
     * @param type the field's type.
     * @param unit a Number field's unit, or `null`.
     * @return the new definition.
     */
    suspend fun addHouseField(houseId: String, name: String, type: FieldType, unit: String?): FieldDef = db.withTransaction {
        val now = clock()
        val def = FieldDef(
            id = newId(), houseId = houseId, name = name, type = type.name,
            unit = unit?.trim()?.ifEmpty { null }?.takeIf { type == FieldType.Number },
            position = dao.houseFields(houseId).size, createdAt = now, updatedAt = now,
        )
        dao.insertDef(def)
        def
    }

    /**
     * Renames a field (and changes its unit); values are kept.
     *
     * @param def the definition.
     * @param name the new name, not empty.
     * @param unit the new unit, or `null`.
     */
    suspend fun rename(def: FieldDef, name: String, unit: String? = def.unit) =
        dao.updateDef(def.copy(name = name, unit = unit?.trim()?.ifEmpty { null }, updatedAt = clock()))

    /**
     * @param defId the definition.
     * @return how many items have a value for it.
     */
    suspend fun valueCount(defId: String): Int = dao.valueCount(defId)

    /**
     * Deletes a field and every value it has.
     *
     * @param defId the definition.
     */
    suspend fun delete(defId: String) = db.withTransaction {
        dao.deleteValuesOf(defId)
        dao.deleteDef(defId)
    }

    /**
     * The fields of an item form: the house-wide ones, then the item's own
     * ones, each with its current value.
     *
     * @param houseId the item's house.
     * @param itemId the item, or `null` for a new item.
     * @param language the app language, for numbers and dates.
     * @return the drafts.
     */
    suspend fun drafts(houseId: String, itemId: String?, language: AppLanguage): List<FieldDraft> {
        val values = itemId?.let { dao.values(it) }.orEmpty().associateBy { it.fieldId }
        val defs = dao.houseFields(houseId) + itemId?.let { dao.itemFields(it) }.orEmpty()
        return defs.map { def ->
            FieldDraft(
                defId = def.id, name = def.name, type = def.fieldType, unit = def.unit, houseWide = def.itemId == null,
                input = values[def.id]?.let { CustomFields.toInput(def.fieldType, it.value, language) }.orEmpty(),
            )
        }
    }

    /**
     * Saves an item's custom fields: creates new per-item fields, stores the
     * values, removes blanked values and per-item fields no longer on the form.
     * Call after [validate] passed.
     *
     * @param houseId the item's house.
     * @param itemId the item.
     * @param drafts the form's fields.
     * @param language the app language, for numbers and dates.
     */
    suspend fun save(houseId: String, itemId: String, drafts: List<FieldDraft>, language: AppLanguage) = db.withTransaction {
        val now = clock()
        val existing = dao.values(itemId).associateBy { it.fieldId }
        val kept = mutableSetOf<String>()
        drafts.forEachIndexed { index, draft ->
            val value = CustomFields.parse(draft.type, draft.input, language).value
            // A per-item field left blank is dropped with its definition.
            if (!draft.houseWide && value == null) return@forEachIndexed
            val defId = draft.defId ?: newId().also { id ->
                dao.insertDef(
                    FieldDef(
                        id = id, houseId = houseId, itemId = itemId, name = draft.name, type = draft.type.name,
                        unit = draft.unit?.trim()?.ifEmpty { null }, position = index, createdAt = now, updatedAt = now,
                    ),
                )
            }
            kept += defId
            val old = existing[defId]
            when {
                value == null && old != null -> dao.deleteValue(old.id)
                value != null && old == null -> dao.insertValue(FieldValue(newId(), houseId, itemId, defId, value, now, now))
                value != null && old != null && old.value != value -> dao.updateValue(old.copy(value = value, updatedAt = now))
            }
        }
        dao.itemFields(itemId).filter { it.id !in kept }.forEach { def ->
            dao.deleteValuesOf(def.id)
            dao.deleteDef(def.id)
        }
    }

    /**
     * Checks the form's values.
     *
     * @param drafts the form's fields.
     * @param language the app language.
     * @return the index of each field with an error, and the error.
     */
    fun validate(drafts: List<FieldDraft>, language: AppLanguage): Map<Int, FieldError> =
        drafts.mapIndexedNotNull { index, draft -> CustomFields.parse(draft.type, draft.input, language).error?.let { index to it } }.toMap()
}
