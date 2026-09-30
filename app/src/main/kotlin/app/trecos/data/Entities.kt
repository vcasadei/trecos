package app.trecos.data

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A house: the special top-level place that every other record belongs to,
 * and the future unit of sharing and sync.
 *
 * @property id random UUID.
 * @property name required display name.
 * @property address optional free-text address.
 * @property description optional description.
 * @property icon key of the place icon shown when there is no photo.
 * @property colorKey key of its [app.trecos.ui.theme.PaletteColor], or `null` for none.
 * @property createdAt creation time, epoch milliseconds.
 * @property updatedAt last change time, epoch milliseconds.
 * @property deletedAt deletion time, or `null` while the house exists.
 */
@Serializable
@Entity(tableName = "house")
data class House(
    @PrimaryKey val id: String,
    val name: String,
    val address: String? = null,
    val description: String? = null,
    val icon: String,
    val colorKey: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * A storage place inside a house; containers nest to any depth.
 *
 * @property id random UUID.
 * @property houseId the house it belongs to.
 * @property parentId the container it sits in, or `null` at the house's top level.
 * @property name required display name.
 * @property description optional description.
 * @property qrCode optional code, unique within the house among items and containers that are not in the trash.
 * @property icon key of the container icon shown when there is no photo.
 * @property colorKey own palette colour key, or `null` to inherit the nearest ancestor's.
 * @property valueOverride manual value in minor units, or `null` for the automatic value.
 * @property createdAt creation time, epoch milliseconds.
 * @property updatedAt last change time, epoch milliseconds.
 * @property deletedAt deletion time, or `null` while it exists.
 */
@Serializable
@Entity(
    tableName = "container",
    indices = [
        Index(value = ["houseId", "parentId"]),
        Index(value = ["houseId", "qrCode"]),
    ],
)
data class Container(
    @PrimaryKey val id: String,
    val houseId: String,
    val parentId: String? = null,
    val name: String,
    val description: String? = null,
    val qrCode: String? = null,
    val icon: String,
    val colorKey: String? = null,
    val valueOverride: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * A thing the user owns. Items sit in a container or at a house's top level
 * and never contain other items.
 *
 * @property id random UUID.
 * @property houseId the house it belongs to.
 * @property containerId the container it sits in, or `null` at the house's top level.
 * @property name required display name.
 * @property quantity whole number of identical units, 0 to 999,999.
 * @property unitPrice value of one unit in minor units, or `null` when unknown.
 * @property brand optional brand.
 * @property model optional model.
 * @property serial optional serial number.
 * @property qrCode optional code, unique within the house among items and containers that are not in the trash.
 * @property description optional description.
 * @property createdAt when the item was added, epoch milliseconds.
 * @property updatedAt when any field last changed, epoch milliseconds.
 * @property deletedAt deletion time, or `null` while it exists.
 */
@Serializable
@Entity(
    tableName = "item",
    indices = [
        Index(value = ["houseId", "containerId"]),
        Index(value = ["houseId", "qrCode"]),
    ],
)
data class Item(
    @PrimaryKey val id: String,
    val houseId: String,
    val containerId: String? = null,
    val name: String,
    val quantity: Int = 1,
    val unitPrice: Long? = null,
    val brand: String? = null,
    val model: String? = null,
    val serial: String? = null,
    val qrCode: String? = null,
    val description: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * The priced total and the number of unpriced items directly inside one
 * container (or a house's top level, when [containerId] is `null`).
 *
 * @property containerId the container, or `null` for the house's top level.
 * @property total sum of quantity × unit price in minor units, over priced items.
 * @property unpriced number of items without a unit price.
 * @property items number of items.
 */
data class ContainerTotal(val containerId: String?, val total: Long, val unpriced: Int, val items: Int = 0)
