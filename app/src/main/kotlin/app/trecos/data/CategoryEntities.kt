package app.trecos.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A category the user created in one house. Built-in categories are not
 * stored: they come from a bundled asset and are referenced by key.
 *
 * @property id random UUID.
 * @property houseId the house it belongs to; other houses don't see it.
 * @property parentId the top-level category it sits under (a built-in key or a
 *   custom category id), or `null` when it is a top level itself.
 * @property name the name, shown exactly as typed in every language.
 * @property icon the icon key, or `null` for the empty default icon.
 * @property createdAt creation time, epoch milliseconds.
 * @property updatedAt last change time, epoch milliseconds.
 */
@Entity(tableName = "category", indices = [Index(value = ["houseId", "parentId"])])
data class CustomCategory(
    @PrimaryKey val id: String,
    val houseId: String,
    val parentId: String? = null,
    val name: String,
    val icon: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One category assigned to an item. Position 0 is the main category.
 *
 * @property id random UUID.
 * @property houseId the item's house.
 * @property itemId the item.
 * @property categoryId a built-in category key or a custom category id.
 * @property position the order on the item; 0 is the main category.
 * @property createdAt creation time, epoch milliseconds.
 */
@Entity(
    tableName = "item_category",
    indices = [Index(value = ["itemId", "categoryId"], unique = true), Index(value = ["houseId", "categoryId"])],
)
data class ItemCategory(
    @PrimaryKey val id: String,
    val houseId: String,
    val itemId: String,
    val categoryId: String,
    val position: Int,
    val createdAt: Long,
)

/**
 * A free-text tag of one house. Tags that differ only in case or accents are
 * the same tag: [normalized] is unique in the house.
 *
 * @property id random UUID.
 * @property houseId the house it belongs to.
 * @property name the name as first typed.
 * @property normalized lower case without accents, for matching.
 * @property createdAt creation time, epoch milliseconds.
 * @property updatedAt last change time, epoch milliseconds.
 */
@Entity(tableName = "tag", indices = [Index(value = ["houseId", "normalized"], unique = true)])
data class Tag(
    @PrimaryKey val id: String,
    val houseId: String,
    val name: String,
    val normalized: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One tag on an item.
 *
 * @property id random UUID.
 * @property houseId the item's house.
 * @property itemId the item.
 * @property tagId the tag.
 * @property createdAt creation time, epoch milliseconds.
 */
@Entity(
    tableName = "item_tag",
    indices = [Index(value = ["itemId", "tagId"], unique = true), Index(value = ["houseId", "tagId"])],
)
data class ItemTag(
    @PrimaryKey val id: String,
    val houseId: String,
    val itemId: String,
    val tagId: String,
    val createdAt: Long,
)

/**
 * How often a word in item names and descriptions came with a category in
 * one house, for suggestions (design D7). Kept on the device.
 *
 * @property houseId the house.
 * @property token a normalised word.
 * @property categoryId a built-in key or a custom category id.
 * @property count how many saved items had both.
 */
@Entity(tableName = "token_category_count", primaryKeys = ["houseId", "token", "categoryId"])
data class TokenCategoryCount(
    val houseId: String,
    val token: String,
    val categoryId: String,
    val count: Int,
)

/**
 * One delete the user can undo or restore from the house's trash. The trashed
 * item or container and everything inside it share the same `deletedAt`
 * ([trashedAt]), which tells them apart from things trashed separately.
 *
 * @property id random UUID.
 * @property houseId the house.
 * @property kind [KIND_ITEM] or [KIND_CONTAINER].
 * @property targetId the trashed item or container.
 * @property name its name when trashed, for the trash list.
 * @property parentId where it was: its container, or `null` for the house's top level.
 * @property trashedAt when it was trashed, epoch milliseconds.
 */
@Entity(tableName = "trash_entry", indices = [Index(value = ["houseId", "trashedAt"])])
data class TrashEntry(
    @PrimaryKey val id: String,
    val houseId: String,
    val kind: String,
    val targetId: String,
    val name: String,
    val parentId: String?,
    val trashedAt: Long,
) {
    companion object {
        /** An item was trashed. */
        const val KIND_ITEM = "item"

        /** A container, with its contents, was trashed. */
        const val KIND_CONTAINER = "container"
    }
}

/**
 * One photo of an item, container or house. The image file is named by its
 * SHA-256, so identical photos are stored once and several rows may share it.
 *
 * @property id random UUID.
 * @property houseId the owner's house.
 * @property ownerType [OWNER_ITEM], [OWNER_CONTAINER] or [OWNER_HOUSE].
 * @property ownerId the owner's id.
 * @property sha256 the stored WebP file's SHA-256, in lower-case hex.
 * @property position the order on the owner; 0 is the main photo.
 * @property createdAt when it was added, epoch milliseconds.
 */
@Entity(tableName = "photo", indices = [Index(value = ["ownerId", "position"]), Index(value = ["sha256"]), Index(value = ["houseId"])])
data class Photo(
    @PrimaryKey val id: String,
    val houseId: String,
    val ownerType: String,
    val ownerId: String,
    val sha256: String,
    val position: Int,
    val createdAt: Long,
) {
    companion object {
        /** The photo belongs to an item. */
        const val OWNER_ITEM = "item"

        /** The photo belongs to a container. */
        const val OWNER_CONTAINER = "container"

        /** The photo belongs to a house. */
        const val OWNER_HOUSE = "house"

        /** The most photos one owner can have. */
        const val MAX_PER_OWNER = 3
    }
}

/**
 * The full-text search index (design D6): one row per item or container that
 * is not in the trash, kept up to date by SQLite triggers ([SearchIndex]).
 * Matching ignores case and accents.
 *
 * @property rowId the FTS row id.
 * @property kind [KIND_ITEM] or [KIND_CONTAINER]; not indexed.
 * @property refId the item or container id; not indexed.
 * @property houseId its house; not indexed.
 * @property name the name plus brand, model, serial number and QR code.
 * @property description the description.
 */
@androidx.room.Fts4(
    tokenizer = androidx.room.FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=1"],
    notIndexed = ["kind", "refId", "houseId"],
)
@Entity(tableName = "search_index")
data class SearchEntry(
    @PrimaryKey(autoGenerate = true) @androidx.room.ColumnInfo(name = "rowid") val rowId: Int = 0,
    val kind: String,
    val refId: String,
    val houseId: String,
    val name: String,
    val description: String,
) {
    companion object {
        /** An item. */
        const val KIND_ITEM = "item"

        /** A container. */
        const val KIND_CONTAINER = "container"
    }
}
