package app.trecos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Custom categories and item-category assignments. */
@Dao
interface CategoryDao {
    /**
     * @param houseId the house.
     * @return the house's custom categories, updating as they change.
     */
    @Query("SELECT * FROM category WHERE houseId = :houseId ORDER BY name COLLATE NOCASE")
    fun observeCustom(houseId: String): Flow<List<CustomCategory>>

    /**
     * @param houseId the house.
     * @return the house's custom categories.
     */
    @Query("SELECT * FROM category WHERE houseId = :houseId")
    suspend fun custom(houseId: String): List<CustomCategory>

    /**
     * @param category the new custom category.
     */
    @Insert
    suspend fun insertCustom(category: CustomCategory)

    /**
     * @param category the custom category with its new values.
     */
    @Update
    suspend fun updateCustom(category: CustomCategory)

    /**
     * Deletes a custom category, its subcategories, and every assignment of them.
     *
     * @param id the custom category id.
     */
    @Transaction
    suspend fun deleteCustom(id: String) {
        val ids = listOf(id) + childIds(id)
        deleteAssignments(ids)
        deleteCustomRows(ids)
    }

    /** @return the ids of custom subcategories under [parentId]. */
    @Query("SELECT id FROM category WHERE parentId = :parentId")
    suspend fun childIds(parentId: String): List<String>

    /** Deletes assignments of the given categories. */
    @Query("DELETE FROM item_category WHERE categoryId IN (:ids)")
    suspend fun deleteAssignments(ids: List<String>)

    /** Deletes the given custom category rows. */
    @Query("DELETE FROM category WHERE id IN (:ids)")
    suspend fun deleteCustomRows(ids: List<String>)

    /**
     * @param categoryId a category id or key.
     * @return how many items have it.
     */
    @Query("SELECT COUNT(*) FROM item_category WHERE categoryId = :categoryId")
    suspend fun usage(categoryId: String): Int

    /**
     * @param itemId the item.
     * @return its category ids, main first.
     */
    @Query("SELECT categoryId FROM item_category WHERE itemId = :itemId ORDER BY position")
    suspend fun forItem(itemId: String): List<String>

    /**
     * @param houseId the house.
     * @return every assignment in the house, updating as they change; for list rows.
     */
    @Query("SELECT * FROM item_category WHERE houseId = :houseId ORDER BY position")
    fun observeAssignments(houseId: String): Flow<List<ItemCategory>>

    /**
     * @param itemId the item.
     * @return its assignments, main first, updating as they change.
     */
    @Query("SELECT * FROM item_category WHERE itemId = :itemId ORDER BY position")
    fun observeForItem(itemId: String): Flow<List<ItemCategory>>

    /** Removes every category from an item. */
    @Query("DELETE FROM item_category WHERE itemId = :itemId")
    suspend fun clearItem(itemId: String)

    /** @param rows assignments to add. */
    @Insert
    suspend fun insertAssignments(rows: List<ItemCategory>)

    /**
     * Replaces an item's categories, keeping their order.
     *
     * @param itemId the item.
     * @param rows the new assignments, with positions from 0.
     */
    @Transaction
    suspend fun replaceForItem(itemId: String, rows: List<ItemCategory>) {
        clearItem(itemId)
        insertAssignments(rows)
    }

    /**
     * @param houseId the house.
     * @param tokens normalised words.
     * @return the learned counts for those words.
     */
    @Query("SELECT * FROM token_category_count WHERE houseId = :houseId AND token IN (:tokens)")
    suspend fun learned(houseId: String, tokens: List<String>): List<TokenCategoryCount>

    /** Adds one to a word-category count, creating it at 1. */
    @Query(
        "INSERT INTO token_category_count (houseId, token, categoryId, count) VALUES (:houseId, :token, :categoryId, 1) " +
            "ON CONFLICT(houseId, token, categoryId) DO UPDATE SET count = count + 1",
    )
    suspend fun learn(houseId: String, token: String, categoryId: String)
}

/** Tags and item-tag assignments. */
@Dao
interface TagDao {
    /**
     * @param houseId the house.
     * @return the house's tags by name, updating as they change.
     */
    @Query("SELECT * FROM tag WHERE houseId = :houseId ORDER BY normalized")
    fun observeAll(houseId: String): Flow<List<Tag>>

    /**
     * @param houseId the house.
     * @return the house's tags.
     */
    @Query("SELECT * FROM tag WHERE houseId = :houseId")
    suspend fun all(houseId: String): List<Tag>

    /**
     * @param houseId the house.
     * @param normalized a normalised tag name.
     * @return the matching tag, or `null`.
     */
    @Query("SELECT * FROM tag WHERE houseId = :houseId AND normalized = :normalized")
    suspend fun find(houseId: String, normalized: String): Tag?

    /** @param tag the new tag. */
    @Insert
    suspend fun insert(tag: Tag)

    /** @param tag the tag with its new name. */
    @Update
    suspend fun update(tag: Tag)

    /** Deletes a tag and removes it from every item. */
    @Transaction
    suspend fun delete(id: String) {
        deleteAssignments(id)
        deleteRow(id)
    }

    /** Removes a tag from every item. */
    @Query("DELETE FROM item_tag WHERE tagId = :tagId")
    suspend fun deleteAssignments(tagId: String)

    /** Deletes a tag row. */
    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun deleteRow(id: String)

    /**
     * Moves every item from one tag to another, skipping items that already
     * have the target, used when a rename makes two tags equal.
     */
    @Query(
        "UPDATE OR IGNORE item_tag SET tagId = :to WHERE tagId = :from",
    )
    suspend fun reassign(from: String, to: String)

    /**
     * @param itemId the item.
     * @return the item's tags by name, updating as they change.
     */
    @Query("SELECT tag.* FROM tag JOIN item_tag ON item_tag.tagId = tag.id WHERE item_tag.itemId = :itemId ORDER BY tag.normalized")
    fun observeForItem(itemId: String): Flow<List<Tag>>

    /**
     * @param itemId the item.
     * @return the item's tags.
     */
    @Query("SELECT tag.* FROM tag JOIN item_tag ON item_tag.tagId = tag.id WHERE item_tag.itemId = :itemId ORDER BY tag.normalized")
    suspend fun forItem(itemId: String): List<Tag>

    /** Removes every tag from an item. */
    @Query("DELETE FROM item_tag WHERE itemId = :itemId")
    suspend fun clearItem(itemId: String)

    /** @param rows item tags to add. */
    @Insert
    suspend fun insertAssignments(rows: List<ItemTag>)

    /**
     * Replaces an item's tags.
     *
     * @param itemId the item.
     * @param rows the new item tags.
     */
    @Transaction
    suspend fun replaceForItem(itemId: String, rows: List<ItemTag>) {
        clearItem(itemId)
        insertAssignments(rows)
    }
}
