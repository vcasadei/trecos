package app.trecos.data

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/** The type of a custom field; stored by name. */
enum class FieldType { Text, Number, Date, YesNo }

/**
 * A custom field definition: house-wide when [itemId] is `null`, otherwise
 * belonging to that one item only.
 *
 * @property id random UUID.
 * @property houseId the house it belongs to.
 * @property itemId the item of a per-item field, or `null` for a house-wide one.
 * @property name the name, shown exactly as typed.
 * @property type a [FieldType] name.
 * @property unit a free-text unit shown after a Number value, or `null`.
 * @property position the order among the house's (or item's) fields.
 * @property createdAt creation time, epoch milliseconds.
 * @property updatedAt last change time, epoch milliseconds.
 */
@Serializable
@Entity(tableName = "field_def", indices = [Index(value = ["houseId", "itemId"])])
data class FieldDef(
    @PrimaryKey val id: String,
    val houseId: String,
    val itemId: String? = null,
    val name: String,
    val type: String,
    val unit: String? = null,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** The parsed type; unknown names read as Text. */
    val fieldType: FieldType get() = runCatching { FieldType.valueOf(type) }.getOrDefault(FieldType.Text)
}

/**
 * One item's value for a custom field, stored as text: numbers as plain
 * decimals ("4", "2.5"), dates as ISO dates ("2026-09-30"), Yes/No as
 * "true" or "false".
 *
 * @property id random UUID.
 * @property houseId the item's house.
 * @property itemId the item.
 * @property fieldId the [FieldDef].
 * @property value the encoded value.
 * @property createdAt creation time, epoch milliseconds.
 * @property updatedAt last change time, epoch milliseconds.
 */
@Serializable
@Entity(
    tableName = "field_value",
    indices = [Index(value = ["itemId", "fieldId"], unique = true), Index(value = ["fieldId"])],
)
data class FieldValue(
    @PrimaryKey val id: String,
    val houseId: String,
    val itemId: String,
    val fieldId: String,
    val value: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * A field with an item's value, for showing on item screens and rows.
 *
 * @property fieldId the definition's id.
 * @property itemId the item.
 * @property name the field name.
 * @property type a [FieldType] name.
 * @property unit the unit, or `null`.
 * @property value the encoded value.
 */
data class ItemFieldValue(val fieldId: String, val itemId: String, val name: String, val type: String, val unit: String?, val value: String)
