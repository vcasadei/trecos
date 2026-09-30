package app.trecos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Reads and writes houses. Deleted houses are never returned. */
@Dao
interface HouseDao {
    /** @return every house, ordered by name, updating as they change. */
    @Query("SELECT * FROM house WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<House>>

    /**
     * @param id the house id.
     * @return the house, updating as it changes, or `null` if missing or deleted.
     */
    @Query("SELECT * FROM house WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<House?>

    /**
     * @param id the house id.
     * @return the house, or `null` if missing or deleted.
     */
    @Query("SELECT * FROM house WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): House?

    /** @return the number of houses that exist. */
    @Query("SELECT COUNT(*) FROM house WHERE deletedAt IS NULL")
    suspend fun count(): Int

    /**
     * Inserts a new house. Fails if its id is already taken, so nothing is
     * ever overwritten silently.
     *
     * @param house the house to add.
     * @throws android.database.sqlite.SQLiteConstraintException if the id or QR code is taken.
     */
    @Insert
    suspend fun insert(house: House)

    /**
     * Saves changes to an existing house.
     *
     * @param house the house with its new values.
     * @throws android.database.sqlite.SQLiteConstraintException if the new QR code is taken.
     */
    @Update
    suspend fun update(house: House)
}

/** Reads and writes containers. Deleted containers are never returned. */
@Dao
interface ContainerDao {
    /**
     * @param houseId the house.
     * @param parentId the parent container, or `null` for the house's top level.
     * @return the containers directly inside that level, ordered by name.
     */
    @Query(
        "SELECT * FROM container WHERE houseId = :houseId AND deletedAt IS NULL " +
            "AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId) ORDER BY name COLLATE NOCASE",
    )
    fun observeChildren(houseId: String, parentId: String?): Flow<List<Container>>

    /**
     * @param houseId the house.
     * @return every container of the house, updating as they change.
     */
    @Query("SELECT * FROM container WHERE houseId = :houseId AND deletedAt IS NULL")
    fun observeAllInHouse(houseId: String): Flow<List<Container>>

    /**
     * @param id the container id.
     * @return the container, updating as it changes, or `null` if missing or deleted.
     */
    @Query("SELECT * FROM container WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<Container?>

    /**
     * @param id the container id.
     * @return the container, or `null` if missing or deleted.
     */
    @Query("SELECT * FROM container WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): Container?

    /**
     * Inserts a new container. Fails if its id is already taken, so nothing
     * is ever overwritten silently; QR codes are checked by [QrDao] first.
     *
     * @param container the container to add.
     * @throws android.database.sqlite.SQLiteConstraintException if the id or QR code is taken.
     */
    @Insert
    suspend fun insert(container: Container)

    /**
     * Saves changes to an existing container.
     *
     * @param container the container with its new values.
     * @throws android.database.sqlite.SQLiteConstraintException if the new QR code is taken.
     */
    @Update
    suspend fun update(container: Container)
}

/** Reads and writes items. Deleted items are never returned. */
@Dao
interface ItemDao {
    /**
     * @param houseId the house.
     * @param containerId the container, or `null` for the house's top level.
     * @return the items directly inside that level, ordered by name.
     */
    @Query(
        "SELECT * FROM item WHERE houseId = :houseId AND deletedAt IS NULL " +
            "AND ((:containerId IS NULL AND containerId IS NULL) OR containerId = :containerId) ORDER BY name COLLATE NOCASE",
    )
    fun observeIn(houseId: String, containerId: String?): Flow<List<Item>>

    /**
     * @param id the item id.
     * @return the item, updating as it changes, or `null` if missing or deleted.
     */
    @Query("SELECT * FROM item WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<Item?>

    /**
     * @param id the item id.
     * @return the item, or `null` if missing or deleted.
     */
    @Query("SELECT * FROM item WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): Item?

    /**
     * Totals every container of a house directly, for the value fold (design D5).
     *
     * @param houseId the house.
     * @return one row per container that holds items, plus one for the top level if it does.
     */
    @Query(
        "SELECT containerId, COALESCE(SUM(quantity * unitPrice), 0) AS total, " +
            "SUM(CASE WHEN unitPrice IS NULL THEN 1 ELSE 0 END) AS unpriced, COUNT(*) AS items " +
            "FROM item WHERE houseId = :houseId AND deletedAt IS NULL GROUP BY containerId",
    )
    fun observeTotals(houseId: String): Flow<List<ContainerTotal>>

    /**
     * Inserts a new item. Fails if its id is already taken, so nothing is
     * ever overwritten silently; QR codes are checked by [QrDao] first.
     *
     * @param item the item to add.
     * @throws android.database.sqlite.SQLiteConstraintException if the id or QR code is taken.
     */
    @Insert
    suspend fun insert(item: Item)

    /**
     * Saves changes to an existing item.
     *
     * @param item the item with its new values.
     * @throws android.database.sqlite.SQLiteConstraintException if the new QR code is taken.
     */
    @Update
    suspend fun update(item: Item)
}

/** Checks QR codes, which are unique within a house across items and containers. */
@Dao
interface QrDao {
    /**
     * @param houseId the house.
     * @param code the code to check.
     * @param exceptId the record being edited, which may keep its own code.
     * @return how many other items or containers in the house use the code, not counting the trash
     *   (a trashed thing keeps its code, but loses it on restore if it was taken meanwhile).
     */
    @Query(
        "SELECT (SELECT COUNT(*) FROM item WHERE houseId = :houseId AND qrCode = :code AND id != :exceptId AND deletedAt IS NULL) + " +
            "(SELECT COUNT(*) FROM container WHERE houseId = :houseId AND qrCode = :code AND id != :exceptId AND deletedAt IS NULL)",
    )
    suspend fun countUses(houseId: String, code: String, exceptId: String): Int
}
