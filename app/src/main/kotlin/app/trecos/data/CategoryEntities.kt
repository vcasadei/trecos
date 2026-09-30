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
