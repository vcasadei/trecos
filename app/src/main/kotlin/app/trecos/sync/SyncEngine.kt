package app.trecos.sync

import androidx.room.withTransaction
import app.trecos.backup.HouseSnapshot
import app.trecos.backup.SnapshotFormat
import app.trecos.backup.deleteHouseRows
import app.trecos.crypto.MissingKeyException
import app.trecos.crypto.SnapshotCipher
import app.trecos.data.Photo
import app.trecos.data.TrecosDatabase
import app.trecos.places.PhotoStore

/**
 * What one sync did.
 *
 * @property commits how many commits this device wrote.
 * @property changedHere how many rows the merge changed on this device.
 * @property conflicts how many new conflicts were found.
 * @property photosWaiting how many photos wait for Wi-Fi (or a later sync).
 * @property remoteOnlyHouses houses in Drive that aren't on this device, for the restore offer.
 */
data class SyncReport(
    val commits: Int = 0,
    val changedHere: Int = 0,
    val conflicts: Int = 0,
    val photosWaiting: Int = 0,
    val remoteOnlyHouses: List<String> = emptyList(),
)

/** The house changed on this device while it was syncing; the next sync picks the change up. */
class ChangedDuringSyncException : Exception("Local data changed during sync")

/**
 * Syncs houses with a [SyncRemote] (design D14): merges the other devices'
 * latest commits into the local data, writes a commit only when something
 * changed, then moves this device's ref. Every step can be interrupted and
 * repeated: commits are content-addressed and the ref moves last.
 *
 * @param db the database.
 * @param photos the stored photos.
 * @param store this device's sync state.
 * @param clock the current time.
 * @param writeKey the key new commits are encrypted with, or `null` when encryption is off.
 * @param readKeys every key that may decrypt older commits.
 */
class SyncEngine(
    private val db: TrecosDatabase,
    private val photos: PhotoStore,
    private val store: SyncStore,
    private val clock: () -> Long,
    private val writeKey: () -> ByteArray? = { null },
    private val readKeys: () -> List<ByteArray> = { emptyList() },
) {

    /**
     * Syncs every house on this device.
     *
     * @param remote the shared store.
     * @param user the Google account, recorded in history.
     * @param photosAllowed whether photos may move now (Wi-Fi, or "Photos only on Wi-Fi" off).
     * @param create whether to create the Drive folder when it is missing (only on the first connection).
     * @param pull houses from Drive to bring onto this device too (after the user chose to restore them).
     * @return what happened.
     * @throws DriveFolderMissingException when the folder is gone and [create] is `false`.
     * @throws NewerFormatException when another device wrote a newer format.
     */
    suspend fun sync(remote: SyncRemote, user: DriveUser, photosAllowed: Boolean, create: Boolean = false, pull: Set<String> = emptySet()): SyncReport {
        try {
            remote.open(create)
        } catch (e: DriveFolderMissingException) {
            store.update { it.copy(driveMissing = true, lastError = e.message) }
            throw e
        }
        val localHouses = db.snapshots().houses().map { it.id }
        val remoteHouses = remote.houses()
        var total = SyncReport(remoteOnlyHouses = remoteHouses.filter { it !in localHouses && it !in pull })
        for (houseId in localHouses + pull.filter { it !in localHouses }) {
            val report = try {
                syncHouse(remote, houseId, user, photosAllowed)
            } catch (e: app.trecos.backup.SnapshotFormatException) {
                // A file in the Trecos folder was changed outside the app: refuse to merge it.
                store.update { it.copy(driveMissing = true, lastError = e.message) }
                throw DriveFolderMissingException("Drive data of house $houseId can't be read")
            }
            total = total.copy(
                commits = total.commits + report.commits,
                changedHere = total.changedHere + report.changedHere,
                conflicts = total.conflicts + report.conflicts,
                photosWaiting = total.photosWaiting + report.photosWaiting,
            )
        }
        store.update { it.copy(lastSuccess = clock(), lastError = null, driveMissing = false) }
        return total
    }

    /**
     * Syncs one house.
     *
     * @param remote the shared store.
     * @param houseId the house (it may exist only in Drive yet).
     * @param user the Google account.
     * @param photosAllowed whether photos may move now.
     * @return what happened.
     */
    suspend fun syncHouse(remote: SyncRemote, houseId: String, user: DriveUser, photosAllowed: Boolean): SyncReport {
        val state = store.load()
        val houseState = state.houses[houseId] ?: HouseSyncState()
        val localRows = localRows(houseId)
        val refs = remote.refs(houseId)
        refs.values.maxOfOrNull { it.formatVersion }?.let { if (it > SnapshotFormat.VERSION) throw NewerFormatException(it) }
        val others = refs.filterKeys { it != state.deviceId }.filter { (device, ref) -> houseState.seen[device] != ref.commitId }
        val headRows = houseState.head?.let { rowsOf(remote, houseId, it) }

        val pairs = others.map { (_, ref) ->
            val theirs = rowsOf(remote, houseId, ref.commitId) ?: throw DriveFolderMissingException("Commit ${ref.commitId} is missing")
            val base = mergeBase(remote, houseId, houseState.head, ref.commitId)?.let { rowsOf(remote, houseId, it) }.orEmpty()
            base to theirs
        }
        val result = Merge.fold(localRows, pairs)
        val merged = repair(result.rows)
        if (merged != localRows) apply(houseId, localRows, merged)

        val waiting = movePhotos(remote, merged, photosAllowed)
        val theirDevice = others.keys.firstOrNull().orEmpty()
        if (result.conflicts.isNotEmpty()) {
            store.update { s ->
                val known = s.conflicts.map { it.houseId to (it.conflict.key to it.conflict.field) }.toSet()
                s.copy(conflicts = s.conflicts + result.conflicts.filter { (houseId to (it.key to it.field)) !in known }.map { StoredConflict(houseId, it, theirDevice) })
            }
        }

        val seen = houseState.seen + others.mapValues { it.value.commitId }
        var commits = 0
        if (merged.isNotEmpty() && (houseState.head == null || headRows == null || merged != headRows)) {
            val parents = listOfNotNull(houseState.head) + others.values.map { it.commitId }
            val id = CommitCodec.commitId(parents, state.deviceId, merged)
            val meta = CommitMeta(
                id = id, parents = parents, time = clock(), deviceId = state.deviceId, deviceName = state.deviceName,
                userName = user.displayName, userEmail = user.emailAddress, formatVersion = SnapshotFormat.VERSION,
                changes = changes(headRows.orEmpty(), merged), conflicts = result.conflicts.size,
            )
            val plain = CommitCodec.encode(meta, merged)
            val bytes = writeKey()?.let { SnapshotCipher.encrypt(plain, it) } ?: plain
            remote.putCommit(houseId, id, bytes)
            cache(houseId, id, bytes)
            remote.putRef(houseId, state.deviceId, Ref(id, meta.time, SnapshotFormat.VERSION))
            store.update { it.copy(houses = it.houses + (houseId to HouseSyncState(id, seen))) }
            commits = 1
            collectGarbage(remote, houseId, keep = id)
        } else {
            store.update { it.copy(houses = it.houses + (houseId to houseState.copy(seen = seen))) }
        }
        return SyncReport(commits, result.changedHere, result.conflicts.size, waiting)
    }

    /**
     * Lists the sync history of the houses on this device: every commit this
     * device knows, newest first.
     *
     * @return the entries.
     */
    fun history(): List<CommitMeta> {
        return store.cachedCommits()
            .mapNotNull { runCatching { CommitCodec.meta(plain(it.readBytes())) }.getOrNull() }
            .distinctBy { it.id }
            .sortedByDescending { it.time }
    }

    /**
     * Changes a house's rows, as resolving a conflict does, repairing broken links.
     *
     * @param houseId the house.
     * @param change builds the new rows from the current ones.
     */
    suspend fun edit(houseId: String, change: (Rows) -> Rows) {
        val rows = localRows(houseId)
        val changed = repair(change(rows))
        if (changed != rows) apply(houseId, rows, changed)
    }

    /** Reads a house's rows from the database, or none when it isn't on this device. */
    private suspend fun localRows(houseId: String): Rows {
        val house = db.houses().get(houseId) ?: return emptyMap()
        return SnapshotFormat.toRows(HouseSnapshot.read(db, house))
    }

    /** Replaces a house's rows, unless the user changed it since [expected] was read. */
    private suspend fun apply(houseId: String, expected: Rows, merged: Rows) = db.withTransaction {
        if (localRows(houseId) != expected) throw ChangedDuringSyncException()
        deleteHouseRows(db, houseId)
        if (merged.isNotEmpty()) SnapshotFormat.fromRows(merged).insertInto(db)
    }

    /**
     * Fixes links a merge may break, such as an item added on one device into a
     * container deleted on another: the item moves to the top level, and rows
     * pointing at things that are gone are dropped.
     */
    private fun repair(rows: Rows): Rows {
        if (rows.isEmpty()) return rows
        val s = SnapshotFormat.fromRows(rows)
        val containers = s.containers.map { it.id }.toSet()
        val items = s.items.map { it.id }.toSet()
        val tags = s.tags.map { it.id }.toSet()
        val fields = s.fieldDefs.map { it.id }.toSet()
        val owners = containers + items + s.house.id
        val fixed = s.copy(
            containers = s.containers.map { if (it.parentId != null && it.parentId !in containers) it.copy(parentId = null) else it },
            items = s.items.map { if (it.containerId != null && it.containerId !in containers) it.copy(containerId = null) else it },
            itemCategories = s.itemCategories.filter { it.itemId in items },
            itemTags = s.itemTags.filter { it.itemId in items && it.tagId in tags },
            fieldDefs = s.fieldDefs.filter { it.itemId == null || it.itemId in items },
            fieldValues = s.fieldValues.filter { it.itemId in items && it.fieldId in fields && s.fieldDefs.any { d -> d.id == it.fieldId && (d.itemId == null || d.itemId in items) } },
            photos = s.photos.filter { it.ownerId in owners },
            trash = s.trash.filter { it.targetId in owners },
        )
        return if (fixed == s) rows else SnapshotFormat.toRows(fixed)
    }

    /** Uploads photos Drive lacks and downloads photos this device lacks; returns how many wait. */
    private suspend fun movePhotos(remote: SyncRemote, rows: Rows, allowed: Boolean): Int {
        if (rows.isEmpty()) return 0
        val hashes = SnapshotFormat.fromRows(rows).photos.map(Photo::sha256).toSet()
        val stored = remote.objects()
        val toUpload = hashes.filter { it !in stored && photos.photoFile(it).exists() }
        val toDownload = hashes.filter { it in stored && !photos.photoFile(it).exists() }
        if (!allowed) return toUpload.size + toDownload.size
        toUpload.forEach { remote.putObject(it, photos.photoFile(it).readBytes()) }
        toDownload.forEach { sha ->
            remote.getObject(sha)?.let { bytes -> photos.photoFile(sha).apply { parentFile?.mkdirs() }.writeBytes(bytes) }
        }
        return 0
    }

    /** @return how many rows differ between two states. */
    private fun changes(before: Rows, after: Rows): Int =
        after.count { (k, v) -> before[k] != v } + before.keys.count { it !in after }

    /** @return a commit's rows, from the cache or Drive, or `null` when it no longer exists. */
    private suspend fun rowsOf(remote: SyncRemote, houseId: String, commitId: String): Rows? =
        bytesOf(remote, houseId, commitId)?.let { CommitCodec.decode(plain(it)).second }

    /**
     * @param bytes a commit file as stored, encrypted or not.
     * @return the plain file.
     * @throws MissingKeyException when it is encrypted and no known key opens it.
     */
    private fun plain(bytes: ByteArray): ByteArray {
        if (!SnapshotCipher.isEncrypted(bytes)) return bytes
        for (key in readKeys()) runCatching { return SnapshotCipher.decrypt(bytes, key) }
        throw MissingKeyException()
    }

    /** @return a commit's file, from the cache or Drive (then cached). */
    private suspend fun bytesOf(remote: SyncRemote, houseId: String, commitId: String): ByteArray? {
        val cached = store.cachedCommit(houseId, commitId)
        if (cached.exists()) return cached.readBytes()
        val bytes = remote.commit(houseId, commitId) ?: return null
        cache(houseId, commitId, bytes)
        return bytes
    }

    /** Keeps a commit file on the device. */
    private fun cache(houseId: String, commitId: String, bytes: ByteArray) {
        store.cachedCommit(houseId, commitId).apply { parentFile?.mkdirs() }.writeBytes(bytes)
    }

    /**
     * Finds the newest commit both histories contain.
     *
     * @return the merge base, or `null` when there is none (or it was removed).
     */
    private suspend fun mergeBase(remote: SyncRemote, houseId: String, mine: String?, theirs: String): String? {
        if (mine == null) return null
        val ancestors = HashSet<String>()
        val queue = ArrayDeque(listOf(mine))
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!ancestors.add(id)) continue
            bytesOf(remote, houseId, id)?.let { queue += CommitCodec.meta(plain(it)).parents }
        }
        val visited = HashSet<String>()
        val search = ArrayDeque(listOf(theirs))
        while (search.isNotEmpty()) {
            val id = search.removeFirst()
            if (id in ancestors) return id
            if (!visited.add(id)) continue
            bytesOf(remote, houseId, id)?.let { search += CommitCodec.meta(plain(it)).parents }
        }
        return null
    }

    /** Deletes this device's commits beyond the newest [KEEP] (spec "Sync history"). */
    private suspend fun collectGarbage(remote: SyncRemote, houseId: String, keep: String) {
        val deviceId = store.load().deviceId
        val mine = store.cachedCommits(houseId)
            .mapNotNull { file -> runCatching { CommitCodec.meta(plain(file.readBytes())) }.getOrNull() }
            .filter { it.deviceId == deviceId }
            .sortedByDescending { it.time }
        mine.drop(KEEP).filter { it.id != keep }.forEach { old ->
            remote.deleteCommit(houseId, old.id)
            store.cachedCommit(houseId, old.id).delete()
        }
    }

    companion object {
        /** How many commits Drive keeps per device. */
        const val KEEP = 30
    }
}
