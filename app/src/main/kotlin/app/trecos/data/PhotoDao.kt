package app.trecos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Photo rows; the image files are handled by `PhotoStore`. */
@Dao
interface PhotoDao {
    /**
     * @param ownerId an item, container or house id.
     * @return its photos, main first, updating as they change.
     */
    @Query("SELECT * FROM photo WHERE ownerId = :ownerId ORDER BY position")
    fun observeFor(ownerId: String): Flow<List<Photo>>

    /**
     * @param ownerId an item, container or house id.
     * @return its photos, main first.
     */
    @Query("SELECT * FROM photo WHERE ownerId = :ownerId ORDER BY position")
    suspend fun forOwner(ownerId: String): List<Photo>

    /**
     * @param houseId a house.
     * @return the main photo of every owner in the house, updating as they change; for list rows.
     */
    @Query("SELECT * FROM photo WHERE houseId = :houseId AND position = 0")
    fun observeMainPhotos(houseId: String): Flow<List<Photo>>

    /** @param photos rows to add. */
    @Insert
    suspend fun insert(photos: List<Photo>)

    /** Removes every photo row of an owner. */
    @Query("DELETE FROM photo WHERE ownerId = :ownerId")
    suspend fun clearOwner(ownerId: String)

    /**
     * Replaces an owner's photos, keeping the given order.
     *
     * @param ownerId the owner.
     * @param photos the photos, with positions from 0.
     */
    @Transaction
    suspend fun replaceFor(ownerId: String, photos: List<Photo>) {
        clearOwner(ownerId)
        insert(photos)
    }

    /** Removes the photo rows of the given owners. */
    @Query("DELETE FROM photo WHERE ownerId IN (:ownerIds)")
    suspend fun deleteOwners(ownerIds: List<String>)

    /** Removes the photo rows of a house. */
    @Query("DELETE FROM photo WHERE houseId = :houseId")
    suspend fun deleteHouse(houseId: String)

    /** @return every file name (SHA-256) still referenced by a row. */
    @Query("SELECT DISTINCT sha256 FROM photo")
    suspend fun referencedHashes(): List<String>

    /** Moves an owner's photos to another house, when the owner moves. */
    @Query("UPDATE photo SET houseId = :houseId WHERE ownerId IN (:ownerIds)")
    suspend fun moveOwners(ownerIds: List<String>, houseId: String)
}
