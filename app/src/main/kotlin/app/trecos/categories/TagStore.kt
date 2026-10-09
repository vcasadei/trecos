package app.trecos.categories

import app.trecos.data.ItemTag
import app.trecos.data.Tag
import app.trecos.data.TagDao

/**
 * Tag rules on top of [TagDao]: tags belong to a house, and names that differ
 * only in case or accents are the same tag.
 *
 * @param dao the tag DAO.
 * @param clock the current time in epoch milliseconds.
 * @param newId generates ids.
 */
class TagStore(private val dao: TagDao, private val clock: () -> Long, private val newId: () -> String) {

    /**
     * Finds the house's tag with this name, ignoring case and accents, or creates it.
     *
     * @param houseId the house.
     * @param name the typed name.
     * @return the existing or new tag, or `null` for a blank name.
     */
    suspend fun findOrCreate(houseId: String, name: String): Tag? {
        val trimmed = name.trim().ifEmpty { return null }
        val normalized = TextNormalizer.normalize(trimmed)
        dao.find(houseId, normalized)?.let { return it }
        val now = clock()
        return Tag(newId(), houseId, trimmed, normalized, now, now).also { dao.insert(it) }
    }

    /**
     * Offers existing tags for what the user is typing.
     *
     * @param houseId the house.
     * @param typed the partial name.
     * @param exclude tag ids already on the item.
     * @return tags whose name starts with the typed text, ignoring case and accents.
     */
    suspend fun complete(houseId: String, typed: String, exclude: Collection<String> = emptyList()): List<Tag> {
        val needle = TextNormalizer.normalize(typed)
        if (needle.isEmpty()) return emptyList()
        return dao.all(houseId).filter { it.normalized.startsWith(needle) && it.id !in exclude }.sortedBy { it.normalized }
    }

    /**
     * Sets an item's tags from typed names, reusing existing tags.
     *
     * @param houseId the item's house.
     * @param itemId the item.
     * @param names the tag names, in any case.
     */
    suspend fun setForItem(houseId: String, itemId: String, names: List<String>) {
        val tags = names.mapNotNull { findOrCreate(houseId, it) }.distinctBy { it.id }
        val now = clock()
        dao.replaceForItem(itemId, tags.map { ItemTag(newId(), houseId, itemId, it.id, now) })
    }

    /**
     * Renames a tag everywhere. If the new name equals another tag (ignoring
     * case and accents), the two are merged into that one.
     *
     * @param tag the tag to rename.
     * @param name the new name.
     */
    suspend fun rename(tag: Tag, name: String) {
        val trimmed = name.trim().ifEmpty { return }
        val normalized = TextNormalizer.normalize(trimmed)
        val existing = dao.find(tag.houseId, normalized)
        if (existing != null && existing.id != tag.id) {
            dao.reassign(from = tag.id, to = existing.id)
            dao.delete(tag.id)
        } else {
            dao.update(tag.copy(name = trimmed, normalized = normalized, updatedAt = clock()))
        }
    }

    /**
     * Deletes a tag and removes it from every item.
     *
     * @param tag the tag.
     */
    suspend fun delete(tag: Tag) = dao.delete(tag.id)
}
