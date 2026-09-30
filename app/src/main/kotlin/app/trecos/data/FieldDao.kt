package app.trecos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Custom field definitions and values. */
@Dao
interface FieldDao {
    /**
     * @param houseId the house.
     * @return the house-wide fields, in order, updating as they change.
     */
    @Query("SELECT * FROM field_def WHERE houseId = :houseId AND itemId IS NULL ORDER BY position, createdAt")
    fun observeHouseFields(houseId: String): Flow<List<FieldDef>>

    /**
     * @param houseId the house.
     * @return the house-wide fields, in order.
     */
    @Query("SELECT * FROM field_def WHERE houseId = :houseId AND itemId IS NULL ORDER BY position, createdAt")
    suspend fun houseFields(houseId: String): List<FieldDef>

    /**
     * @param itemId the item.
     * @return the item's own fields, in order.
     */
    @Query("SELECT * FROM field_def WHERE itemId = :itemId ORDER BY position, createdAt")
    suspend fun itemFields(itemId: String): List<FieldDef>

    /**
     * @param id the definition id.
     * @return the definition, or `null`.
     */
    @Query("SELECT * FROM field_def WHERE id = :id")
    suspend fun def(id: String): FieldDef?

    /** @param def the definition to add. */
    @Insert
    suspend fun insertDef(def: FieldDef)

    /** @param def the definition to change. */
    @Update
    suspend fun updateDef(def: FieldDef)

    /** @param id the definition to delete (its values are deleted separately). */
    @Query("DELETE FROM field_def WHERE id = :id")
    suspend fun deleteDef(id: String)

    /**
     * @param fieldId the definition.
     * @return how many items have a value for it.
     */
    @Query("SELECT COUNT(*) FROM field_value WHERE fieldId = :fieldId")
    suspend fun valueCount(fieldId: String): Int

    /**
     * @param itemId the item.
     * @return the item's values.
     */
    @Query("SELECT * FROM field_value WHERE itemId = :itemId")
    suspend fun values(itemId: String): List<FieldValue>

    /** @param value the value to add. */
    @Insert
    suspend fun insertValue(value: FieldValue)

    /** @param value the value to change. */
    @Update
    suspend fun updateValue(value: FieldValue)

    /** @param id the value to delete. */
    @Query("DELETE FROM field_value WHERE id = :id")
    suspend fun deleteValue(id: String)

    /** @param fieldId the definition whose values are deleted. */
    @Query("DELETE FROM field_value WHERE fieldId = :fieldId")
    suspend fun deleteValuesOf(fieldId: String)

    /**
     * @param itemId the item.
     * @return the item's filled-in fields with their definitions, house-wide first.
     */
    @Query(
        "SELECT d.id AS fieldId, v.itemId, d.name, d.type, d.unit, v.value FROM field_value v JOIN field_def d ON d.id = v.fieldId " +
            "WHERE v.itemId = :itemId ORDER BY d.itemId IS NOT NULL, d.position, d.createdAt",
    )
    fun observeForItem(itemId: String): Flow<List<ItemFieldValue>>

    /**
     * @param houseId the house.
     * @return every filled-in value of the house with its definition, for detailed rows.
     */
    @Query(
        "SELECT d.id AS fieldId, v.itemId, d.name, d.type, d.unit, v.value FROM field_value v JOIN field_def d ON d.id = v.fieldId " +
            "WHERE v.houseId = :houseId",
    )
    fun observeForHouse(houseId: String): Flow<List<ItemFieldValue>>

    /** @return every filled-in value of every house with its definition, for search rows. */
    @Query("SELECT d.id AS fieldId, v.itemId, d.name, d.type, d.unit, v.value FROM field_value v JOIN field_def d ON d.id = v.fieldId")
    fun observeAll(): Flow<List<ItemFieldValue>>

    /**
     * @param houseId the house.
     * @param name the field name.
     * @param type a [FieldType] name.
     * @return the house-wide field with that name (ignoring case) and type, or `null`.
     */
    @Query("SELECT * FROM field_def WHERE houseId = :houseId AND itemId IS NULL AND name = :name COLLATE NOCASE AND type = :type LIMIT 1")
    suspend fun findHouseField(houseId: String, name: String, type: String): FieldDef?

    /** @param itemIds items whose values and own fields are deleted. */
    @Query("DELETE FROM field_value WHERE itemId IN (:itemIds)")
    suspend fun deleteValuesOfItems(itemIds: List<String>)

    /** @param itemIds items whose own field definitions are deleted. */
    @Query("DELETE FROM field_def WHERE itemId IN (:itemIds)")
    suspend fun deleteDefsOfItems(itemIds: List<String>)

    /** @param houseId the house whose values are deleted. */
    @Query("DELETE FROM field_value WHERE houseId = :houseId")
    suspend fun deleteHouseValues(houseId: String)

    /** @param houseId the house whose field definitions are deleted. */
    @Query("DELETE FROM field_def WHERE houseId = :houseId")
    suspend fun deleteHouseDefs(houseId: String)
}
