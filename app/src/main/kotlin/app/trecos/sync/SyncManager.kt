package app.trecos.sync

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Build
import androidx.room.withTransaction
import app.trecos.R
import app.trecos.backup.deleteHouseRows
import app.trecos.data.AppPreferences
import app.trecos.data.TrecosDatabase
import app.trecos.places.PhotoStore
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What connecting found (spec "Restoring onto a device").
 */
sealed interface ConnectResult {
    /**
     * Connected and synced.
     *
     * @property report what the first sync did.
     */
    data class Synced(val report: SyncReport) : ConnectResult

    /**
     * Drive has houses and this device has only the empty first house: offer to restore them.
     *
     * @property houses the Drive houses, id to name.
     */
    data class OfferRestore(val houses: Map<String, String>) : ConnectResult

    /**
     * Both Drive and this device have data: offer to merge, or keep only the Drive data.
     *
     * @property houses the Drive houses not on this device, id to name.
     */
    data class OfferMerge(val houses: Map<String, String>) : ConnectResult

    /** The user closed the Google screen: sync stays off. */
    data object Cancelled : ConnectResult

    /**
     * It didn't work.
     *
     * @property message what to tell the user.
     */
    data class Failed(val message: String) : ConnectResult
}

/**
 * The sync status screens show.
 *
 * @property state the stored state.
 * @property running whether a sync is running now.
 */
data class SyncStatus(val state: SyncState, val running: Boolean = false)

/**
 * Runs sync for the app: connecting and restoring, "Sync now", background
 * syncs and disconnecting. Only one sync runs at a time.
 *
 * @param context the application context.
 * @param db the database.
 * @param photos the stored photos.
 * @param preferences device preferences.
 * @param resources for plain-language errors.
 * @param clock the current time.
 */
class SyncManager(
    private val context: Context,
    private val db: TrecosDatabase,
    photos: PhotoStore,
    private val preferences: AppPreferences,
    private val resources: Resources,
    clock: () -> Long,
) {
    /** This device's sync state. */
    val store = SyncStore(File(context.filesDir, "sync"), Build.MODEL ?: "Android")

    /** The merge and upload logic. */
    val engine = SyncEngine(db, photos, store, clock)

    /** Access to Drive; tests replace it. */
    var auth: DriveAuth = GoogleDriveAuth

    /** Opens the remote store and reads the account for a token; tests replace it. */
    var connectTo: (token: String) -> Pair<SyncRemote, suspend () -> DriveUser> = { token ->
        val client = DriveClient(token = { token })
        DriveRemote(client) to { client.user() }
    }

    /** Whether photos may move now; tests replace it. */
    var onUnmeteredNetwork: () -> Boolean = {
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == false
    }

    private val mutex = Mutex()
    private val statusState = MutableStateFlow(SyncStatus(store.load()))

    /** The current status. */
    val status: StateFlow<SyncStatus> = statusState.asStateFlow()

    /**
     * Connects with a token the user just granted, and syncs or offers to restore.
     *
     * @param token the Drive access token.
     * @return what happened.
     */
    suspend fun connect(token: String): ConnectResult = run {
        val (remote, user) = connectTo(token)
        val existed = remote.open(create = true)
        val account = user()
        store.update { it.copy(connected = true, accountEmail = account.emailAddress) }
        if (!existed) return@run ConnectResult.Synced(syncWith(remote, account, create = true))
        val local = db.snapshots().houses().map { it.id }.toSet()
        val onlyInDrive = remote.houses().filter { it !in local }
        if (onlyInDrive.isEmpty()) return@run ConnectResult.Synced(syncWith(remote, account))
        val names = houseNames(remote, onlyInDrive)
        if (hasOnlyAnEmptyHouse()) ConnectResult.OfferRestore(names) else ConnectResult.OfferMerge(names)
    }

    /**
     * Finishes a connection that offered a choice.
     *
     * @param token the Drive access token.
     * @param houses the Drive houses to bring here.
     * @param replaceLocal whether to delete this device's houses first ("Keep only the Drive data", or restoring over the empty first house).
     * @return the sync report.
     */
    suspend fun restore(token: String, houses: Set<String>, replaceLocal: Boolean): ConnectResult = run {
        val (remote, user) = connectTo(token)
        remote.open(create = false)
        if (replaceLocal) {
            db.withTransaction { db.snapshots().houses().forEach { deleteHouseRows(db, it.id) } }
            store.update { s -> s.copy(houses = s.houses.filterKeys { it in houses }) }
        }
        ConnectResult.Synced(syncWith(remote, user(), pull = houses))
    }

    /**
     * Syncs now, in the foreground or from the background job.
     *
     * @param token a token, or `null` to get one silently.
     * @return `true` when it succeeded.
     */
    suspend fun syncNow(token: String? = null): Boolean {
        if (!store.load().connected) return false
        val access = token ?: auth.token(context)
        if (access == null) {
            store.update { it.copy(lastError = resources.getString(R.string.sync_error_auth)) }
            refresh()
            return false
        }
        val result = run {
            val (remote, user) = connectTo(access)
            ConnectResult.Synced(syncWith(remote, user()))
        }
        return result is ConnectResult.Synced
    }

    /**
     * Re-creates the Drive folder and uploads this device's data, after it was deleted or altered.
     *
     * @param token a token, or `null` to get one silently.
     * @return `true` when it succeeded.
     */
    suspend fun uploadAgain(token: String? = null): Boolean {
        val access = token ?: auth.token(context) ?: return false
        store.update { it.copy(houses = emptyMap(), driveMissing = false) }
        val result = run {
            val (remote, user) = connectTo(access)
            ConnectResult.Synced(syncWith(remote, user(), create = true))
        }
        return result is ConnectResult.Synced
    }

    /** Stops syncing; every house stays on the phone (spec "Connecting Google Drive"). */
    fun disconnect() {
        store.update { it.copy(connected = false, accountEmail = "", lastError = null) }
        refresh()
    }

    /**
     * Resolves a conflict (spec "Merging and conflicts"). "Keep mine" leaves
     * the local value, which the next sync shares; "Keep theirs" puts the other
     * device's value in its place.
     *
     * @param stored the conflict.
     * @param keepTheirs whether to take the other device's value.
     */
    suspend fun resolve(stored: StoredConflict, keepTheirs: Boolean) = withContext(Dispatchers.IO) {
        if (keepTheirs) engine.edit(stored.houseId) { rows -> theirsApplied(rows, stored.conflict) }
        store.update { s -> s.copy(conflicts = s.conflicts - stored) }
        refresh()
    }

    /** @return the rows with the other device's side of a conflict. */
    private fun theirsApplied(rows: Rows, conflict: Conflict): Rows {
        val theirs = conflict.theirs
        return when (conflict.kind) {
            ConflictKind.SameField -> {
                val row = rows[conflict.key] ?: return rows
                val field = conflict.field ?: return rows
                rows + (conflict.key to JsonObject(row + (field to (theirs ?: JsonNull))))
            }
            ConflictKind.EditVersusDelete -> when (theirs) {
                is JsonObject -> {
                    val restored = rows + (conflict.key to theirs)
                    val untrashed = theirs["deletedAt"] == null || theirs["deletedAt"] is JsonNull
                    if (untrashed) restored.filterNot { (k, v) -> k.startsWith("trash_entry/") && v["targetId"]?.jsonPrimitive?.content == conflict.rowKey } else restored
                }
                else -> rows - conflict.key
            }
        }
    }

    /**
     * Renames this device in the sync history.
     *
     * @param name the new name.
     */
    fun rename(name: String) {
        store.update { it.copy(deviceName = name.trim().ifEmpty { it.deviceName }) }
        refresh()
    }

    /** Re-reads the stored state into [status]. */
    fun refresh() {
        statusState.value = statusState.value.copy(state = store.load())
    }

    /** Runs one sync with the photo rule applied. */
    private suspend fun syncWith(remote: SyncRemote, user: DriveUser, create: Boolean = false, pull: Set<String> = emptySet()): SyncReport {
        val photosAllowed = !preferences.photosOnlyOnWifi.first() || onUnmeteredNetwork()
        return engine.sync(remote, user, photosAllowed, create, pull)
    }

    /** Runs [block] alone, turning failures into plain-language errors. */
    private suspend fun run(block: suspend () -> ConnectResult): ConnectResult = mutex.withLock {
        statusState.value = SyncStatus(store.load(), running = true)
        try {
            block()
        } catch (e: Exception) {
            val message = messageFor(e)
            store.update { it.copy(lastError = message) }
            ConnectResult.Failed(message)
        } finally {
            statusState.value = SyncStatus(store.load(), running = false)
        }
    }

    /** @return what to tell the user about a failure. */
    private fun messageFor(e: Exception): String = resources.getString(
        when (e) {
            is DriveAuthException -> R.string.sync_error_auth
            is NewerFormatException -> R.string.sync_error_newer
            is DriveFolderMissingException -> R.string.sync_error_missing
            is IOException -> R.string.sync_error_network
            else -> R.string.sync_error_other
        },
    )

    /** @return whether this device has only the empty first house. */
    private suspend fun hasOnlyAnEmptyHouse(): Boolean {
        val houses = db.snapshots().houses()
        if (houses.size > 1) return false
        val house = houses.singleOrNull() ?: return true
        return db.snapshots().items(house.id).isEmpty() && db.snapshots().containers(house.id).isEmpty()
    }

    /** @return each house's name from its newest commit. */
    private suspend fun houseNames(remote: SyncRemote, ids: List<String>): Map<String, String> = ids.associateWith { id ->
        val ref = remote.refs(id).values.maxByOrNull { it.time }
        val bytes = ref?.let { remote.commit(id, it.commitId) }
        bytes?.let { runCatching { CommitCodec.decode(it).second["house/$id"]?.get("name")?.toString()?.trim('"') }.getOrNull() } ?: id
    }
}
