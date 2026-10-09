package app.trecos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Reads and writes whole houses, for backups and sync snapshots. Trashed rows are included. */
@Dao
interface SnapshotDao {
    /** @return every house, by name. */
    @Query("SELECT * FROM house ORDER BY name COLLATE NOCASE")
    suspend fun houses(): List<House>

    /**
     * @param houseId the house.
     * @return its containers, trashed ones too.
     */
    @Query("SELECT * FROM container WHERE houseId = :houseId")
    suspend fun containers(houseId: String): List<Container>

    /**
     * @param houseId the house.
     * @return its items, trashed ones too.
     */
    @Query("SELECT * FROM item WHERE houseId = :houseId")
    suspend fun items(houseId: String): List<Item>

    /**
     * @param houseId the house.
     * @return its custom categories.
     */
    @Query("SELECT * FROM category WHERE houseId = :houseId")
    suspend fun categories(houseId: String): List<CustomCategory>

    /**
     * @param houseId the house.
     * @return its items' category assignments.
     */
    @Query("SELECT * FROM item_category WHERE houseId = :houseId")
    suspend fun itemCategories(houseId: String): List<ItemCategory>

    /**
     * @param houseId the house.
     * @return its tags.
     */
    @Query("SELECT * FROM tag WHERE houseId = :houseId")
    suspend fun tags(houseId: String): List<Tag>

    /**
     * @param houseId the house.
     * @return its items' tag assignments.
     */
    @Query("SELECT * FROM item_tag WHERE houseId = :houseId")
    suspend fun itemTags(houseId: String): List<ItemTag>

    /**
     * @param houseId the house.
     * @return its learned suggestion counts.
     */
    @Query("SELECT * FROM token_category_count WHERE houseId = :houseId")
    suspend fun learned(houseId: String): List<TokenCategoryCount>

    /**
     * @param houseId the house.
     * @return its trash entries.
     */
    @Query("SELECT * FROM trash_entry WHERE houseId = :houseId")
    suspend fun trash(houseId: String): List<TrashEntry>

    /**
     * @param houseId the house.
     * @return its photo rows.
     */
    @Query("SELECT * FROM photo WHERE houseId = :houseId")
    suspend fun photos(houseId: String): List<Photo>

    /**
     * @param houseId the house.
     * @return its custom field definitions.
     */
    @Query("SELECT * FROM field_def WHERE houseId = :houseId")
    suspend fun fieldDefs(houseId: String): List<FieldDef>

    /**
     * @param houseId the house.
     * @return its custom field values.
     */
    @Query("SELECT * FROM field_value WHERE houseId = :houseId")
    suspend fun fieldValues(houseId: String): List<FieldValue>

    /** @param rows houses to add. */
    @Insert
    suspend fun insertHouses(rows: List<House>)

    /** @param rows containers to add. */
    @Insert
    suspend fun insertContainers(rows: List<Container>)

    /** @param rows items to add. */
    @Insert
    suspend fun insertItems(rows: List<Item>)

    /** @param rows categories to add. */
    @Insert
    suspend fun insertCategories(rows: List<CustomCategory>)

    /** @param rows assignments to add. */
    @Insert
    suspend fun insertItemCategories(rows: List<ItemCategory>)

    /** @param rows tags to add. */
    @Insert
    suspend fun insertTags(rows: List<Tag>)

    /** @param rows assignments to add. */
    @Insert
    suspend fun insertItemTags(rows: List<ItemTag>)

    /** @param rows counts to add. */
    @Insert
    suspend fun insertLearned(rows: List<TokenCategoryCount>)

    /** @param rows trash entries to add. */
    @Insert
    suspend fun insertTrash(rows: List<TrashEntry>)

    /** @param rows photo rows to add. */
    @Insert
    suspend fun insertPhotos(rows: List<Photo>)

    /** @param rows definitions to add. */
    @Insert
    suspend fun insertFieldDefs(rows: List<FieldDef>)

    /** @param rows values to add. */
    @Insert
    suspend fun insertFieldValues(rows: List<FieldValue>)

    /** Deletes every custom field value; the first step of "Replace everything". */
    @Query("DELETE FROM field_value")
    suspend fun clearFieldValues()

    /** Deletes every custom field definition. */
    @Query("DELETE FROM field_def")
    suspend fun clearFieldDefs()

    /** Deletes every photo row. */
    @Query("DELETE FROM photo")
    suspend fun clearPhotos()

    /** Deletes every trash entry. */
    @Query("DELETE FROM trash_entry")
    suspend fun clearTrash()

    /** Deletes every learned count. */
    @Query("DELETE FROM token_category_count")
    suspend fun clearLearned()

    /** Deletes every tag assignment. */
    @Query("DELETE FROM item_tag")
    suspend fun clearItemTags()

    /** Deletes every tag. */
    @Query("DELETE FROM tag")
    suspend fun clearTags()

    /** Deletes every category assignment. */
    @Query("DELETE FROM item_category")
    suspend fun clearItemCategories()

    /** Deletes every custom category. */
    @Query("DELETE FROM category")
    suspend fun clearCategories()

    /** Deletes every item. */
    @Query("DELETE FROM item")
    suspend fun clearItems()

    /** Deletes every container. */
    @Query("DELETE FROM container")
    suspend fun clearContainers()

    /** Deletes every house. */
    @Query("DELETE FROM house")
    suspend fun clearHouses()
}
