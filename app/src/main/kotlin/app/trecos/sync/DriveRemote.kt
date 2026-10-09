package app.trecos.sync

import kotlinx.serialization.json.Json

/**
 * [SyncRemote] in the user's Drive (design D14), in a visible folder:
 * `Trecos/houses/<houseId>/commits/<id>.jsonl.gz`,
 * `Trecos/houses/<houseId>/refs/<deviceId>.json`, `Trecos/houses/<houseId>/deleted.json`
 * while a house is deleted, and `Trecos/objects/<sha256>.webp`.
 * With the `drive.file` scope the app sees only files it created itself.
 *
 * @param drive the Drive API.
 */
class DriveRemote(private val drive: DriveFiles) : SyncRemote {
    private val json = Json { ignoreUnknownKeys = true }
    private val folders = HashMap<String, String>()
    private var root: String? = null

    override suspend fun open(create: Boolean): Boolean {
        val found = drive.list("name = '$ROOT_NAME' and mimeType = '${DriveClient.FOLDER}' and 'root' in parents and trashed = false").firstOrNull()
        if (found != null) {
            root = found.id
            return true
        }
        if (!create) throw DriveFolderMissingException("The $ROOT_NAME folder is missing from Drive")
        root = drive.createFolder(ROOT_NAME, null).id
        folders.clear()
        return false
    }

    override suspend fun houses(): List<String> =
        folder(listOf(HOUSES), create = false)?.let { children(it).filter { f -> f.mimeType == DriveClient.FOLDER }.map { f -> f.name } }.orEmpty()

    override suspend fun refs(houseId: String): Map<String, Ref> {
        val dir = folder(listOf(HOUSES, houseId, REFS), create = false) ?: return emptyMap()
        return children(dir).filter { it.name.endsWith(".json") }.associate { file ->
            file.name.removeSuffix(".json") to json.decodeFromString(Ref.serializer(), drive.download(file.id).decodeToString())
        }
    }

    override suspend fun putRef(houseId: String, deviceId: String, ref: Ref) {
        val dir = folder(listOf(HOUSES, houseId, REFS), create = true)!!
        val bytes = json.encodeToString(Ref.serializer(), ref).toByteArray()
        val existing = find(dir, "$deviceId.json")
        if (existing != null) drive.update(existing.id, bytes, "application/json") else drive.upload("$deviceId.json", dir, bytes, "application/json")
    }

    override suspend fun commit(houseId: String, commitId: String): ByteArray? {
        val dir = folder(listOf(HOUSES, houseId, COMMITS), create = false) ?: return null
        return find(dir, "$commitId$COMMIT_EXT")?.let { drive.download(it.id) }
    }

    override suspend fun putCommit(houseId: String, commitId: String, bytes: ByteArray) {
        val dir = folder(listOf(HOUSES, houseId, COMMITS), create = true)!!
        if (find(dir, "$commitId$COMMIT_EXT") == null) drive.upload("$commitId$COMMIT_EXT", dir, bytes, "application/gzip")
    }

    override suspend fun commits(houseId: String): List<String> {
        val dir = folder(listOf(HOUSES, houseId, COMMITS), create = false) ?: return emptyList()
        return children(dir).map { it.name }.filter { it.endsWith(COMMIT_EXT) }.map { it.removeSuffix(COMMIT_EXT) }
    }

    override suspend fun deleteCommit(houseId: String, commitId: String) {
        val dir = folder(listOf(HOUSES, houseId, COMMITS), create = false) ?: return
        find(dir, "$commitId$COMMIT_EXT")?.let { drive.delete(it.id) }
    }

    override suspend fun deletion(houseId: String): Deletion? {
        val dir = folder(listOf(HOUSES, houseId), create = false) ?: return null
        return find(dir, DELETED)?.let { json.decodeFromString(Deletion.serializer(), drive.download(it.id).decodeToString()) }
    }

    override suspend fun markDeleted(houseId: String, deletion: Deletion) {
        val dir = folder(listOf(HOUSES, houseId), create = true)!!
        val bytes = json.encodeToString(Deletion.serializer(), deletion).toByteArray()
        val existing = find(dir, DELETED)
        if (existing != null) drive.update(existing.id, bytes, "application/json") else drive.upload(DELETED, dir, bytes, "application/json")
    }

    override suspend fun clearDeleted(houseId: String) {
        val dir = folder(listOf(HOUSES, houseId), create = false) ?: return
        find(dir, DELETED)?.let { drive.delete(it.id) }
    }

    override suspend fun objects(): Set<String> {
        val dir = folder(listOf(OBJECTS), create = false) ?: return emptySet()
        return children(dir).map { it.name }.filter { it.endsWith(PHOTO_EXT) }.map { it.removeSuffix(PHOTO_EXT) }.toSet()
    }

    override suspend fun putObject(sha256: String, bytes: ByteArray) {
        val dir = folder(listOf(OBJECTS), create = true)!!
        if (find(dir, "$sha256$PHOTO_EXT") == null) drive.upload("$sha256$PHOTO_EXT", dir, bytes, "image/webp")
    }

    override suspend fun getObject(sha256: String): ByteArray? {
        val dir = folder(listOf(OBJECTS), create = false) ?: return null
        return find(dir, "$sha256$PHOTO_EXT")?.let { drive.download(it.id) }
    }

    /** @return the files directly in a folder. */
    private suspend fun children(folderId: String): List<DriveFile> = drive.list("'$folderId' in parents and trashed = false")

    /** @return the file with that name in a folder, or `null`. */
    private suspend fun find(folderId: String, name: String): DriveFile? =
        drive.list("'$folderId' in parents and name = '${name.replace("'", "\\'")}' and trashed = false").firstOrNull()

    /**
     * Finds (or creates) a folder below the Trecos folder.
     *
     * @param path the folder names from the Trecos folder down.
     * @param create whether to create missing folders.
     * @return the folder id, or `null` when it is missing and [create] is `false`.
     */
    private suspend fun folder(path: List<String>, create: Boolean): String? {
        var parent = root ?: throw IllegalStateException("open() first")
        val walked = ArrayList<String>()
        for (name in path) {
            walked += name
            val key = walked.joinToString("/")
            val known = folders[key]
            if (known != null) {
                parent = known
                continue
            }
            val found = drive.list("'$parent' in parents and name = '$name' and mimeType = '${DriveClient.FOLDER}' and trashed = false").firstOrNull()
            val id = found?.id ?: if (create) drive.createFolder(name, parent).id else return null
            folders[key] = id
            parent = id
        }
        return parent
    }

    private companion object {
        const val ROOT_NAME = "Trecos"
        const val HOUSES = "houses"
        const val REFS = "refs"
        const val COMMITS = "commits"
        const val OBJECTS = "objects"
        const val DELETED = "deleted.json"
        const val COMMIT_EXT = ".jsonl.gz"
        const val PHOTO_EXT = ".webp"
    }
}
