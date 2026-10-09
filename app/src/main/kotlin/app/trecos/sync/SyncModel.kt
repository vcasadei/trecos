package app.trecos.sync

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One sync of one house by one device (design D14).
 *
 * @property id the commit id: a hash of its parents, device and content, so repeating an interrupted sync writes the same commit.
 * @property parents the commits it merged: this device's previous one first, then the other devices' heads.
 * @property time when it was made, epoch milliseconds.
 * @property deviceId the app-generated id of the device that made it.
 * @property deviceName the device's editable name, such as "Pixel 6".
 * @property userName the Google account's name.
 * @property userEmail the Google account's e-mail address.
 * @property formatVersion the snapshot format version of its content.
 * @property changes how many rows changed since this device's previous commit.
 * @property conflicts how many conflicts the merge found.
 */
@Serializable
data class CommitMeta(
    val id: String,
    val parents: List<String>,
    val time: Long,
    val deviceId: String,
    val deviceName: String,
    val userName: String = "",
    val userEmail: String = "",
    val formatVersion: Int,
    val changes: Int = 0,
    val conflicts: Int = 0,
)

/**
 * A device's latest commit of a house; only that device writes it (design D14).
 *
 * @property commitId the commit.
 * @property time when it was written.
 * @property formatVersion the format of the commit.
 */
@Serializable
data class Ref(val commitId: String, val time: Long, val formatVersion: Int)

/**
 * The marker of a house deleted on some device: `houses/<houseId>/deleted.json`.
 * It holds no name, so nothing readable is left when encryption is on.
 *
 * @property time when the house was deleted.
 * @property deviceId the device that deleted it.
 */
@Serializable
data class Deletion(val time: Long, val deviceId: String)

/**
 * What this device remembers about one house's sync.
 *
 * @property head this device's latest commit, or `null` before the first sync.
 * @property seen the commit of each other device merged last.
 */
@Serializable
data class HouseSyncState(val head: String? = null, val seen: Map<String, String> = emptyMap())

/**
 * A conflict waiting for the user.
 *
 * @property houseId the house it is in.
 * @property conflict the disagreement.
 * @property theirDevice the device the other value came from.
 */
@Serializable
data class StoredConflict(val houseId: String, val conflict: Conflict, val theirDevice: String = "")

/**
 * The device's sync state, kept in app-private storage.
 *
 * @property deviceId an app-generated id, never a hardware id (spec "Sync history").
 * @property deviceName the editable device name.
 * @property connected whether a Google account is connected.
 * @property accountEmail the connected account, for labelling; not a secret.
 * @property houses per-house state.
 * @property conflicts conflicts waiting for the user.
 * @property lastSuccess when the last sync finished, or `null`.
 * @property lastError the last failure, in plain language, or `null` after a success.
 * @property driveMissing whether the last sync found the Drive folder deleted or altered.
 * @property pendingDeletes houses deleted here that Drive doesn't mark as deleted yet.
 * @property droppedHouses houses removed here because another device deleted them; they come back if restored.
 */
@Serializable
data class SyncState(
    val deviceId: String = UUID.randomUUID().toString(),
    val deviceName: String = "",
    val connected: Boolean = false,
    val accountEmail: String = "",
    val houses: Map<String, HouseSyncState> = emptyMap(),
    val conflicts: List<StoredConflict> = emptyList(),
    val lastSuccess: Long? = null,
    val lastError: String? = null,
    val driveMissing: Boolean = false,
    val pendingDeletes: Set<String> = emptySet(),
    val droppedHouses: Set<String> = emptySet(),
)

/**
 * Keeps [SyncState] in a file, written atomically, and a cache of commits
 * this device made or downloaded (to find merge bases without downloading again).
 *
 * @param dir the folder, in app-private storage.
 * @param defaultDeviceName the device name used until the user edits it.
 */
class SyncStore(private val dir: File, private val defaultDeviceName: String) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val file = File(dir, "state.json")
    private val lock = Any()

    /** @return the current state; a fresh one (with a new device id) the first time. */
    fun load(): SyncState = synchronized(lock) {
        val state = runCatching { json.decodeFromString(SyncState.serializer(), file.readText()) }.getOrNull()
            ?: SyncState(deviceName = defaultDeviceName).also(::write)
        state
    }

    /**
     * Changes the state.
     *
     * @param change builds the new state from the current one.
     * @return the new state.
     */
    fun update(change: (SyncState) -> SyncState): SyncState = synchronized(lock) {
        change(load()).also(::write)
    }

    /** Writes through a temporary file and a rename, so a crash never leaves half a file. */
    private fun write(state: SyncState) {
        dir.mkdirs()
        val tmp = File(dir, "state.json.tmp")
        tmp.writeText(json.encodeToString(SyncState.serializer(), state))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    /**
     * @param houseId the house.
     * @param commitId the commit.
     * @return the cached commit file (it may not exist).
     */
    fun cachedCommit(houseId: String, commitId: String): File = File(dir, "commits/$houseId/$commitId.jsonl.gz")

    /**
     * @param houseId a house, or `null` for every house.
     * @return the cached commit files.
     */
    fun cachedCommits(houseId: String? = null): List<File> {
        val root = File(dir, "commits")
        val dirs = if (houseId != null) listOf(File(root, houseId)) else root.listFiles().orEmpty().toList()
        return dirs.flatMap { it.listFiles().orEmpty().toList() }.filter { it.name.endsWith(".jsonl.gz") }
    }
}
