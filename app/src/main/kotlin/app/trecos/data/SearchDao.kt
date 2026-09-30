package app.trecos.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One full-text match.
 *
 * @property kind [SearchEntry.KIND_ITEM] or [SearchEntry.KIND_CONTAINER].
 * @property refId the item or container id.
 */
data class SearchHit(val kind: String, val refId: String)

/** Full-text queries on `search_index`. */
@Dao
interface SearchDao {
    /**
     * @param match an FTS4 match expression, such as `rasp* 4*` or `name:sn4478*`.
     * @return the matching items and containers, updating as data changes.
     */
    @Query("SELECT kind, refId FROM search_index WHERE search_index MATCH :match")
    fun observeMatches(match: String): Flow<List<SearchHit>>

    /**
     * @param match an FTS4 match expression.
     * @return the matching items and containers.
     */
    @Query("SELECT kind, refId FROM search_index WHERE search_index MATCH :match")
    suspend fun matches(match: String): List<SearchHit>

    /** @return every item that is not in the trash, in every house, updating as they change. */
    @Query("SELECT * FROM item WHERE deletedAt IS NULL")
    fun observeAllItems(): Flow<List<Item>>

    /** @return every container that is not in the trash, in every house, updating as they change. */
    @Query("SELECT * FROM container WHERE deletedAt IS NULL")
    fun observeAllContainers(): Flow<List<Container>>

    /** @return every category assignment, updating as they change. */
    @Query("SELECT * FROM item_category")
    fun observeAllAssignments(): Flow<List<ItemCategory>>

    /** @return every item-tag pair as (item id, normalised tag name), updating as they change. */
    @Query("SELECT item_tag.itemId AS itemId, tag.normalized AS tag FROM item_tag JOIN tag ON tag.id = item_tag.tagId")
    fun observeItemTags(): Flow<List<ItemTagName>>

    /** @return every custom category of every house, updating as they change. */
    @Query("SELECT * FROM category")
    fun observeAllCustomCategories(): Flow<List<CustomCategory>>

    /** @return every tag of every house, updating as they change. */
    @Query("SELECT * FROM tag ORDER BY normalized")
    fun observeAllTags(): Flow<List<Tag>>

    /** @return the main photo of every owner, updating as they change. */
    @Query("SELECT * FROM photo WHERE position = 0")
    fun observeAllMainPhotos(): Flow<List<Photo>>
}

/**
 * An item and one of its tags.
 *
 * @property itemId the item.
 * @property tag the tag's normalised name.
 */
data class ItemTagName(val itemId: String, val tag: String)
