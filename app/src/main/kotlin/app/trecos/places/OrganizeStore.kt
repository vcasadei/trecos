package app.trecos.places

import app.trecos.categories.TagStore
import app.trecos.categories.TextNormalizer
import app.trecos.data.Container
import app.trecos.data.CustomCategory
import app.trecos.data.Item
import app.trecos.data.ItemCategory
import app.trecos.data.ItemTag
import app.trecos.data.TrashEntry
import androidx.room.withTransaction
import app.trecos.data.TrecosDatabase

/**
 * Where moved or copied things go.
 *
 * @property houseId the destination house.
 * @property containerId the destination container, or `null` for the house's top level.
 */
data class Destination(val houseId: String, val containerId: String?)

/**
 * What an action applies to: any mix of items and containers.
 *
 * @property itemIds the items.
 * @property containerIds the containers; each brings everything inside it.
 */
data class Selection(val itemIds: List<String> = emptyList(), val containerIds: List<String> = emptyList()) {
    /** Whether nothing is selected. */
    val isEmpty: Boolean get() = itemIds.isEmpty() && containerIds.isEmpty()
}

/**
 * A moved QR code that is already used in the destination house.
 *
 * @property id the moved item or container.
 * @property name its name.
 * @property code the clashing code.
 */
data class QrClash(val id: String, val name: String, val code: String)

/**
 * The outcome of a restore.
 *
 * @property needsDestination the original place no longer exists or is in the trash; ask where to restore, then call again with a destination.
 * @property qrRemoved restored things lost their QR code because something else now uses it.
 */
data class RestoreResult(val needsDestination: Boolean = false, val qrRemoved: Boolean = false)

/** Raised when a container would be moved into itself or one of its own containers. */
class MoveIntoItselfException : IllegalArgumentException("A container can't be moved into itself or into a container inside it")

/**
 * Moving, copying, duplicating, the trash and house deletion, on top of the
 * database. Every rule of the organize and trash specs lives here, so screens
 * only choose what and where.
 *
 * @param db the database.
 * @param clock the current time in epoch milliseconds.
 * @param newId generates ids.
 */
class OrganizeStore(private val db: TrecosDatabase, private val clock: () -> Long, private val newId: () -> String) {

    private val dao = db.organize()
    private val tagStore = TagStore(db.tags(), clock, newId)
    private var lastTrashStamp = 0L

    /**
     * A trash time for a new delete, later than every earlier one, so two
     * deletes in the same millisecond never merge into one batch.
     *
     * @return the time to stamp the delete with.
     */
    @Synchronized
    private fun trashStamp(): Long = maxOf(clock(), lastTrashStamp + 1).also { lastTrashStamp = it }

    /**
     * Everything inside the given containers, at any depth, trashed or not.
     *
     * @param houseId their house.
     * @param rootIds the containers.
     * @return the containers below (roots excluded) and the items below.
     */
    private suspend fun below(houseId: String, rootIds: Collection<String>): Pair<List<Container>, List<Item>> {
        val containers = dao.containersIncludingTrash(houseId)
        val children = containers.groupBy { it.parentId }
        val found = ArrayList<Container>()
        val queue = ArrayDeque(rootIds)
        val seen = HashSet(rootIds)
        while (queue.isNotEmpty()) {
            children[queue.removeFirst()].orEmpty().forEach { if (seen.add(it.id)) { found += it; queue += it.id } }
        }
        val holders = rootIds.toSet() + found.map { it.id }
        return found to dao.itemsIncludingTrash(houseId).filter { it.containerId in holders }
    }

    /**
     * @param containerId a container.
     * @return how many containers and items are inside it at any depth, not counting the trash.
     */
    suspend fun contentCount(containerId: String): Int {
        val container = db.containers().get(containerId) ?: return 0
        val (containers, items) = below(container.houseId, listOf(containerId))
        return containers.count { it.deletedAt == null } + items.count { it.deletedAt == null }
    }

    /**
     * @param selection the things to move.
     * @param destination where they would go.
     * @return whether the destination is inside one of the selected containers, or is one of them.
     */
    suspend fun isInsideSelection(selection: Selection, destination: Destination): Boolean {
        val target = destination.containerId ?: return false
        if (target in selection.containerIds) return true
        val houseId = db.containers().get(selection.containerIds.firstOrNull() ?: return false)?.houseId ?: return false
        return below(houseId, selection.containerIds).first.any { it.id == target }
    }

    /**
     * Lists the QR codes that would clash in the destination house.
     *
     * @param selection the things to move.
     * @param destination where they would go.
     * @return one entry per moved thing whose code is already used there; empty within the same house.
     */
    suspend fun qrClashes(selection: Selection, destination: Destination): List<QrClash> {
        val (containers, items) = collect(selection)
        val moving = containers.map { Triple(it.id, it.name, it.qrCode) to it.houseId } +
            items.map { Triple(it.id, it.name, it.qrCode) to it.houseId }
        val ids = moving.map { (thing, _) -> thing.first }
        return moving
            .filter { (_, house) -> house != destination.houseId }
            .mapNotNull { (thing, _) -> thing.third?.let { code -> QrClash(thing.first, thing.second, code) } }
            .filter { dao.activeQrUses(destination.houseId, it.code, ids) > 0 }
    }

    /**
     * The selected things plus everything inside the selected containers.
     *
     * @param selection the selection.
     * @return every affected container and item, roots included.
     */
    private suspend fun collect(selection: Selection): Pair<List<Container>, List<Item>> {
        val roots = selection.containerIds.mapNotNull { dao.containerAnyState(it) }
        val rootItems = selection.itemIds.mapNotNull { dao.itemAnyState(it) }
        val nested = roots.groupBy { it.houseId }.map { (house, list) -> below(house, list.map { it.id }) }
        return (roots + nested.flatMap { it.first }) to (rootItems + nested.flatMap { it.second })
    }

    /**
     * Moves things to a destination, updating their last-changed date. Moving
     * to another house carries custom categories and tags along, creating them
     * there when missing.
     *
     * @param selection the things to move.
     * @param destination where they go.
     * @param qrResolutions for clashing codes (see [qrClashes]): the id mapped to a new code, or to `null` to remove it.
     * @throws MoveIntoItselfException if a container would go inside itself.
     * @throws IllegalStateException if a clash is left unresolved.
     */
    suspend fun move(selection: Selection, destination: Destination, qrResolutions: Map<String, String?> = emptyMap()) = db.withTransaction {
        if (isInsideSelection(selection, destination)) throw MoveIntoItselfException()
        val unresolved = qrClashes(selection, destination).filter { it.id !in qrResolutions }
        check(unresolved.isEmpty()) { "Unresolved QR clashes: ${unresolved.map { it.code }}" }
        val now = clock()
        val (containers, items) = collect(selection)
        val roots = selection.containerIds.toSet() + selection.itemIds
        for (container in containers) {
            val crossing = container.houseId != destination.houseId
            val isRoot = container.id in roots
            if (!crossing && !isRoot) continue
            db.containers().update(
                container.copy(
                    houseId = destination.houseId,
                    parentId = if (isRoot) destination.containerId else container.parentId,
                    qrCode = if (container.id in qrResolutions) qrResolutions[container.id] else container.qrCode,
                    updatedAt = now,
                ),
            )
        }
        for (item in items) {
            val crossing = item.houseId != destination.houseId
            val isRoot = item.id in roots
            if (!crossing && !isRoot) continue
            if (crossing) carryCategoriesAndTags(item, destination.houseId)
            db.items().update(
                item.copy(
                    houseId = destination.houseId,
                    containerId = if (isRoot) destination.containerId else item.containerId,
                    qrCode = if (item.id in qrResolutions) qrResolutions[item.id] else item.qrCode,
                    updatedAt = now,
                ),
            )
        }
    }

    /**
     * Copies things to a destination with new identities and no QR codes.
     * Copied containers include copies of everything inside them (not what is
     * in the trash).
     *
     * @param selection the things to copy.
     * @param destination where the copies go.
     * @param nameSuffix appended to the copied roots' names, such as " (copy)"; empty by default.
     * @return the ids of the copied roots.
     */
    suspend fun copy(selection: Selection, destination: Destination, nameSuffix: String = ""): List<String> = db.withTransaction {
        val now = clock()
        val (containers, items) = collect(selection)
        val live = containers.filter { it.deletedAt == null }
        val newIds = live.associate { it.id to newId() }
        val roots = selection.containerIds.toSet() + selection.itemIds
        val result = ArrayList<String>()
        for (container in live.sortedBy { depth(it, live) }) {
            val isRoot = container.id in roots
            db.containers().insert(
                container.copy(
                    id = newIds.getValue(container.id),
                    houseId = destination.houseId,
                    parentId = if (isRoot) destination.containerId else newIds[container.parentId] ?: destination.containerId,
                    name = if (isRoot) container.name + nameSuffix else container.name,
                    qrCode = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (isRoot) result += newIds.getValue(container.id)
        }
        for (item in items.filter { it.deletedAt == null }) {
            val isRoot = item.id in roots
            if (!isRoot && item.containerId !in newIds) continue
            val copy = item.copy(
                id = newId(),
                houseId = destination.houseId,
                containerId = if (isRoot) destination.containerId else newIds.getValue(item.containerId!!),
                name = if (isRoot) item.name + nameSuffix else item.name,
                qrCode = null,
                createdAt = now,
                updatedAt = now,
            )
            db.items().insert(copy)
            copyCategoriesAndTags(from = item, to = copy)
            if (isRoot) result += copy.id
        }
        result
    }

    /**
     * Copies one item or container in its own place, with [suffix] after its name.
     *
     * @param id the item or container.
     * @param suffix the translated " (copy)" suffix.
     * @return the new copy's id.
     */
    suspend fun duplicate(id: String, suffix: String): String {
        dao.itemAnyState(id)?.let { item ->
            return copy(Selection(itemIds = listOf(id)), Destination(item.houseId, item.containerId), suffix).single()
        }
        val container = requireNotNull(dao.containerAnyState(id)) { "Nothing to duplicate with id $id" }
        return copy(Selection(containerIds = listOf(id)), Destination(container.houseId, container.parentId), suffix).single()
    }

    /**
     * Moves things to their house's trash. A container takes everything
     * inside it; things already in the trash keep their own entry.
     *
     * @param selection the things to trash.
     * @return the new trash entries' ids, for Undo.
     */
    suspend fun trash(selection: Selection): List<String> = db.withTransaction {
        val now = trashStamp()
        val entries = ArrayList<String>()
        for (itemId in selection.itemIds) {
            val item = db.items().get(itemId) ?: continue
            db.items().update(item.copy(deletedAt = now, updatedAt = now))
            entries += TrashEntry(newId(), item.houseId, TrashEntry.KIND_ITEM, item.id, item.name, item.containerId, now).also { dao.insertTrash(it) }.id
        }
        for (containerId in selection.containerIds) {
            val container = db.containers().get(containerId) ?: continue
            val (containers, items) = below(container.houseId, listOf(container.id))
            (containers.filter { it.deletedAt == null } + container).forEach { db.containers().update(it.copy(deletedAt = now, updatedAt = now)) }
            items.filter { it.deletedAt == null }.forEach { db.items().update(it.copy(deletedAt = now, updatedAt = now)) }
            entries += TrashEntry(newId(), container.houseId, TrashEntry.KIND_CONTAINER, container.id, container.name, container.parentId, now)
                .also { dao.insertTrash(it) }.id
        }
        return@withTransaction entries
    }

    /**
     * Restores a trash entry with what was trashed with it, to its original
     * place or to [destination].
     *
     * @param entryId the trash entry.
     * @param destination where to restore when the original place is gone; ignored otherwise.
     * @return whether a destination is needed first, and whether QR codes were removed.
     */
    suspend fun restore(entryId: String, destination: Destination? = null): RestoreResult = db.withTransaction {
        val entry = dao.trashEntry(entryId) ?: return@withTransaction RestoreResult()
        val originalGone = entry.parentId != null && dao.containerAnyState(entry.parentId).let { it == null || it.deletedAt != null }
        if (originalGone && destination == null) return@withTransaction RestoreResult(needsDestination = true)
        val parent = if (originalGone) destination!!.containerId else entry.parentId
        val now = clock()
        val (containers, items) = when (entry.kind) {
            TrashEntry.KIND_ITEM -> emptyList<Container>() to listOfNotNull(dao.itemAnyState(entry.targetId))
            else -> {
                val root = dao.containerAnyState(entry.targetId)
                val (below, inside) = below(entry.houseId, listOf(entry.targetId))
                (listOfNotNull(root) + below) to inside
            }
        }
        val batchContainers = containers.filter { it.deletedAt == entry.trashedAt }
        val batchItems = items.filter { it.deletedAt == entry.trashedAt }
        val ids = batchContainers.map { it.id } + batchItems.map { it.id }
        var qrRemoved = false
        suspend fun code(code: String?): String? {
            if (code == null || dao.activeQrUses(entry.houseId, code, ids) == 0) return code
            qrRemoved = true
            return null
        }
        for (container in batchContainers) {
            db.containers().update(
                container.copy(
                    deletedAt = null,
                    parentId = if (container.id == entry.targetId) parent else container.parentId,
                    qrCode = code(container.qrCode),
                    updatedAt = now,
                ),
            )
        }
        for (item in batchItems) {
            db.items().update(
                item.copy(
                    deletedAt = null,
                    containerId = if (item.id == entry.targetId) parent else item.containerId,
                    qrCode = code(item.qrCode),
                    updatedAt = now,
                ),
            )
        }
        dao.deleteTrashEntry(entry.id)
        return@withTransaction RestoreResult(qrRemoved = qrRemoved)
    }

    /**
     * Permanently deletes one trash entry and what was trashed with it.
     *
     * @param entryId the trash entry.
     */
    suspend fun deletePermanently(entryId: String) = db.withTransaction {
        val entry = dao.trashEntry(entryId) ?: return@withTransaction
        val (containers, items) = when (entry.kind) {
            TrashEntry.KIND_ITEM -> emptyList<Container>() to listOfNotNull(dao.itemAnyState(entry.targetId))
            else -> below(entry.houseId, listOf(entry.targetId)).let { (below, inside) -> (listOfNotNull(dao.containerAnyState(entry.targetId)) + below) to inside }
        }
        val itemIds = items.filter { it.deletedAt == entry.trashedAt }.map { it.id }
        dao.deleteCategoriesOf(itemIds)
        dao.deleteTagsOf(itemIds)
        dao.hardDeleteItems(itemIds)
        dao.hardDeleteContainers(containers.filter { it.deletedAt == entry.trashedAt }.map { it.id })
        dao.deleteTrashEntry(entry.id)
    }

    /**
     * Permanently deletes everything in a house's trash.
     *
     * @param entryIds the house's trash entries.
     */
    suspend fun emptyTrash(entryIds: List<String>) = entryIds.forEach { deletePermanently(it) }

    /**
     * Permanently deletes whatever has been in any trash for more than 30 days.
     *
     * @param now the current time.
     * @return how many trash entries were purged.
     */
    suspend fun purge(now: Long = clock()): Int {
        val old = dao.trashBefore(now - RETENTION_MS)
        old.forEach { deletePermanently(it.id) }
        return old.size
    }

    /**
     * Permanently deletes a house with its contents, trash, categories and tags.
     *
     * @param houseId the house.
     * @throws IllegalStateException if it is the last house.
     */
    suspend fun deleteHouse(houseId: String) = db.withTransaction {
        check(db.houses().count() > 1) { "The last house can't be deleted" }
        with(dao) {
            deleteHouseItemCategories(houseId)
            deleteHouseItemTags(houseId)
            deleteHouseItems(houseId)
            deleteHouseContainers(houseId)
            deleteHouseCategories(houseId)
            deleteHouseTags(houseId)
            deleteHouseTrash(houseId)
            deleteHouseLearned(houseId)
            deleteHouseRow(houseId)
        }
    }

    /**
     * Puts an item's custom categories and tags into another house: existing
     * ones there (same name, ignoring case and accents) are reused, missing
     * ones created. Built-in categories stay as they are.
     *
     * @param item the item being moved.
     * @param targetHouse the destination house.
     */
    private suspend fun carryCategoriesAndTags(item: Item, targetHouse: String) {
        val mapped = dao.categoriesOf(item.id).map {
            it.copy(id = newId(), houseId = targetHouse, categoryId = mapCategory(it.categoryId, item.houseId, targetHouse))
        }
        db.categories().replaceForItem(item.id, mapped)
        tagStore.setForItem(targetHouse, item.id, dao.tagsOf(item.id).map { it.name })
    }

    /**
     * Gives a copied item the original's categories and tags, carried to the
     * copy's house when it differs.
     *
     * @param from the original item.
     * @param to the copy.
     */
    private suspend fun copyCategoriesAndTags(from: Item, to: Item) {
        val now = clock()
        val rows = dao.categoriesOf(from.id).map {
            ItemCategory(newId(), to.houseId, to.id, mapCategory(it.categoryId, from.houseId, to.houseId), it.position, now)
        }
        db.categories().insertAssignments(rows)
        val names = dao.tagsOf(from.id).map { it.name }
        val tags = names.mapNotNull { tagStore.findOrCreate(to.houseId, it) }.distinctBy { it.id }
        db.tags().insertAssignments(tags.map { ItemTag(newId(), to.houseId, to.id, it.id, now) })
    }

    /**
     * Maps a category id from one house to another: built-in keys stay; a
     * custom category becomes the target house's one with the same name under
     * the same parent, created when missing.
     *
     * @param id a built-in key or custom category id.
     * @param fromHouse the source house.
     * @param toHouse the target house.
     * @return the id to use in the target house.
     */
    private suspend fun mapCategory(id: String, fromHouse: String, toHouse: String): String {
        if (fromHouse == toHouse) return id
        val source = db.categories().custom(fromHouse).firstOrNull { it.id == id } ?: return id
        val parent = source.parentId?.let { mapCategory(it, fromHouse, toHouse) }
        val key = TextNormalizer.normalize(source.name)
        db.categories().custom(toHouse).firstOrNull { TextNormalizer.normalize(it.name) == key && it.parentId == parent }?.let { return it.id }
        val now = clock()
        return CustomCategory(newId(), toHouse, parent, source.name, source.icon, now, now).also { db.categories().insertCustom(it) }.id
    }

    /**
     * @param container a container among [all].
     * @param all the containers being copied.
     * @return how many of [all] are above it, so parents are copied before children.
     */
    private fun depth(container: Container, all: List<Container>): Int {
        val byId = all.associateBy { it.id }
        var depth = 0
        var parent = container.parentId?.let(byId::get)
        while (parent != null && depth <= all.size) {
            depth++
            parent = parent.parentId?.let(byId::get)
        }
        return depth
    }

    companion object {
        /** How long things stay in the trash: 30 days. */
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
