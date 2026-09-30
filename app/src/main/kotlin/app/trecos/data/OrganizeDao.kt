package app.trecos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Low-level queries behind moving, copying, the trash and house deletion. */
@Dao
interface OrganizeDao {
    /**
     * @param houseId the house.
     * @return every container of the house, trashed ones included.
     */
    @Query("SELECT * FROM container WHERE houseId = :houseId")
    suspend fun containersIncludingTrash(houseId: String): List<Container>

    /**
     * @param houseId the house.
     * @return every item of the house, trashed ones included.
     */
    @Query("SELECT * FROM item WHERE houseId = :houseId")
    suspend fun itemsIncludingTrash(houseId: String): List<Item>

    /** @return the container with this id, trashed or not. */
    @Query("SELECT * FROM container WHERE id = :id")
    suspend fun containerAnyState(id: String): Container?

    /** @return the item with this id, trashed or not. */
    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun itemAnyState(id: String): Item?

    /**
     * @param houseId the house.
     * @param code a QR code.
     * @param exceptIds records to ignore, such as the ones being restored.
     * @return how many items and containers that exist (not trashed) use the code.
     */
    @Query(
        "SELECT (SELECT COUNT(*) FROM item WHERE houseId = :houseId AND qrCode = :code AND deletedAt IS NULL AND id NOT IN (:exceptIds)) + " +
            "(SELECT COUNT(*) FROM container WHERE houseId = :houseId AND qrCode = :code AND deletedAt IS NULL AND id NOT IN (:exceptIds))",
    )
    suspend fun activeQrUses(houseId: String, code: String, exceptIds: List<String>): Int

    /** @param entry a new trash entry. */
    @Insert
    suspend fun insertTrash(entry: TrashEntry)

    /** @return the trash entry, or `null`. */
    @Query("SELECT * FROM trash_entry WHERE id = :id")
    suspend fun trashEntry(id: String): TrashEntry?

    /**
     * @param houseId the house.
     * @return the house's trash, newest first, updating as it changes.
     */
    @Query("SELECT * FROM trash_entry WHERE houseId = :houseId ORDER BY trashedAt DESC")
    fun observeTrash(houseId: String): Flow<List<TrashEntry>>

    /**
     * @param before a time, epoch milliseconds.
     * @return trash entries of every house trashed before it.
     */
    @Query("SELECT * FROM trash_entry WHERE trashedAt < :before")
    suspend fun trashBefore(before: Long): List<TrashEntry>

    /** @param id the trash entry to forget. */
    @Query("DELETE FROM trash_entry WHERE id = :id")
    suspend fun deleteTrashEntry(id: String)

    /** Permanently removes items by id. */
    @Query("DELETE FROM item WHERE id IN (:ids)")
    suspend fun hardDeleteItems(ids: List<String>)

    /** Permanently removes containers by id. */
    @Query("DELETE FROM container WHERE id IN (:ids)")
    suspend fun hardDeleteContainers(ids: List<String>)

    /** Removes category assignments of the given items. */
    @Query("DELETE FROM item_category WHERE itemId IN (:itemIds)")
    suspend fun deleteCategoriesOf(itemIds: List<String>)

    /** Removes tag assignments of the given items. */
    @Query("DELETE FROM item_tag WHERE itemId IN (:itemIds)")
    suspend fun deleteTagsOf(itemIds: List<String>)

    /** @return the category assignments of an item, main first. */
    @Query("SELECT * FROM item_category WHERE itemId = :itemId ORDER BY position")
    suspend fun categoriesOf(itemId: String): List<ItemCategory>

    /** @return the tags of an item. */
    @Query("SELECT tag.* FROM tag JOIN item_tag ON item_tag.tagId = tag.id WHERE item_tag.itemId = :itemId")
    suspend fun tagsOf(itemId: String): List<Tag>

    /** Deletes everything of one house, permanently. */
    @Query("DELETE FROM item WHERE houseId = :houseId")
    suspend fun deleteHouseItems(houseId: String)

    /** @see deleteHouseItems */
    @Query("DELETE FROM container WHERE houseId = :houseId")
    suspend fun deleteHouseContainers(houseId: String)

    /** @see deleteHouseItems */
    @Query("DELETE FROM item_category WHERE houseId = :houseId")
    suspend fun deleteHouseItemCategories(houseId: String)

    /** @see deleteHouseItems */
    @Query("DELETE FROM category WHERE houseId = :houseId")
    suspend fun deleteHouseCategories(houseId: String)

    /** @see deleteHouseItems */
    @Query("DELETE FROM item_tag WHERE houseId = :houseId")
    suspend fun deleteHouseItemTags(houseId: String)

    /** @see deleteHouseItems */
    @Query("DELETE FROM tag WHERE houseId = :houseId")
    suspend fun deleteHouseTags(houseId: String)

    /** @see deleteHouseItems */
    @Query("DELETE FROM trash_entry WHERE houseId = :houseId")
    suspend fun deleteHouseTrash(houseId: String)

    /** @see deleteHouseItems */
    @Query("DELETE FROM token_category_count WHERE houseId = :houseId")
    suspend fun deleteHouseLearned(houseId: String)

    /** @see deleteHouseItems */
    @Query("DELETE FROM house WHERE id = :houseId")
    suspend fun deleteHouseRow(houseId: String)
}
