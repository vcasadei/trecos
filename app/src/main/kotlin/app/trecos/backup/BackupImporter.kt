package app.trecos.backup

import androidx.room.withTransaction
import app.trecos.data.Photo
import app.trecos.data.TrecosDatabase
import app.trecos.places.PhotoStore
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Why a backup can't be imported. */
sealed class BackupProblem(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The file isn't a readable Trecos backup. */
    class Invalid(message: String, cause: Throwable? = null) : BackupProblem(message, cause)

    /**
     * The backup was made by a newer app version.
     *
     * @property version the backup's format version.
     */
    class Newer(val version: Int) : BackupProblem("Backup format $version is newer than ${SnapshotFormat.VERSION}")
}

/**
 * A backup read into memory and checked, ready to preview and import.
 *
 * @property manifest what it holds.
 * @property houses every house's rows.
 * @property photoDir where its photos were unpacked, one `<sha256>.webp` each.
 */
class BackupContents(val manifest: Manifest, val houses: List<HouseSnapshot>, val photoDir: File) {
    /** Removes the unpacked photos. */
    fun discard() {
        photoDir.deleteRecursively()
    }
}

/**
 * Reads and imports backups (spec "Import"): everything is read and checked
 * before anything on the phone changes, and the database part runs in one
 * transaction, so an import is all or nothing.
 *
 * @param db the database.
 * @param photos the stored photos.
 * @param workDir a private folder for unpacking.
 * @param newId makes new ids for "Add as new houses".
 */
class BackupImporter(
    private val db: TrecosDatabase,
    private val photos: PhotoStore,
    private val workDir: File,
    private val newId: () -> String,
) {

    /**
     * Reads a backup.
     *
     * @param input the zip; it is closed at the end.
     * @return the checked contents.
     * @throws BackupProblem when it is damaged or too new.
     */
    suspend fun read(input: InputStream): BackupContents = withContext(Dispatchers.IO) {
        val dir = File(workDir, "import-${System.nanoTime()}").apply { mkdirs() }
        try {
            var manifest: Manifest? = null
            val snapshotTexts = LinkedHashMap<String, String>()
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == BackupLayout.MANIFEST -> manifest = BackupLayout.json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString())
                        name.startsWith("houses/") && name.endsWith("/snapshot.jsonl") -> snapshotTexts[name] = zip.readBytes().decodeToString()
                        name.startsWith("objects/") && name.endsWith(".webp") -> unpackPhoto(name.removePrefix("objects/").removeSuffix(".webp"), zip, dir)
                        else -> throw BackupProblem.Invalid("Unexpected entry $name")
                    }
                }
            }
            val found = manifest ?: throw BackupProblem.Invalid("No manifest")
            if (found.formatVersion > SnapshotFormat.VERSION) throw BackupProblem.Newer(found.formatVersion)
            val houses = found.houses.map { entry ->
                val text = snapshotTexts[BackupLayout.snapshot(entry.id)] ?: throw BackupProblem.Invalid("No snapshot for house ${entry.id}")
                SnapshotFormat.read(text.reader().buffered()).also {
                    if (it.house.id != entry.id) throw BackupProblem.Invalid("Snapshot of ${entry.id} holds another house")
                }
            }
            if (houses.isEmpty()) throw BackupProblem.Invalid("No houses")
            BackupContents(found, houses, dir)
        } catch (e: BackupProblem) {
            dir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw BackupProblem.Invalid("The backup can't be read", e)
        }
    }

    /** Writes one photo, checking that its bytes match its name. */
    private fun unpackPhoto(sha: String, zip: ZipInputStream, dir: File) {
        if (!Regex("[0-9a-f]{64}").matches(sha)) throw BackupProblem.Invalid("Bad photo name $sha")
        val bytes = zip.readBytes()
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (actual != sha) throw BackupProblem.Invalid("Photo $sha is damaged")
        File(dir, "$sha.webp").writeBytes(bytes)
    }

    /**
     * Deletes everything on the phone and puts the backup in its place.
     *
     * @param contents a backup from [read]; discarded afterwards.
     */
    suspend fun replaceEverything(contents: BackupContents) = importing(contents) {
        with(db.snapshots()) {
            clearFieldValues()
            clearFieldDefs()
            clearPhotos()
            clearTrash()
            clearLearned()
            clearItemTags()
            clearTags()
            clearItemCategories()
            clearCategories()
            clearItems()
            clearContainers()
            clearHouses()
        }
        contents.houses.forEach { it.insertInto(db) }
    }

    /**
     * Adds the backup's houses next to the existing ones, with new ids everywhere.
     *
     * @param contents a backup from [read]; discarded afterwards.
     * @return the new houses' ids.
     */
    suspend fun addAsNewHouses(contents: BackupContents): List<String> {
        val renewed = contents.houses.map(::withNewIds)
        importing(contents) { renewed.forEach { it.insertInto(db) } }
        return renewed.map { it.house.id }
    }

    /** Copies the photos in (extra files are harmless), then runs [write] in one transaction. */
    private suspend fun importing(contents: BackupContents, write: suspend () -> Unit) = withContext(Dispatchers.IO) {
        try {
            contents.photoDir.listFiles().orEmpty().forEach { file ->
                val target = photos.photoFile(file.nameWithoutExtension)
                if (!target.exists()) {
                    target.parentFile?.mkdirs()
                    file.copyTo(target)
                }
            }
            db.withTransaction { write() }
        } finally {
            contents.discard()
        }
    }

    /**
     * Gives a house and every row in it new ids, keeping every link between them.
     *
     * @param s the house as backed up.
     * @return the same house with new identities.
     */
    private fun withNewIds(s: HouseSnapshot): HouseSnapshot {
        val ids = HashMap<String, String>()
        fun new(old: String) = ids.getOrPut(old) { newId() }
        fun mapped(old: String?) = old?.let { ids[it] ?: it }
        val houseId = new(s.house.id)
        s.containers.forEach { new(it.id) }
        s.items.forEach { new(it.id) }
        s.categories.forEach { new(it.id) }
        s.tags.forEach { new(it.id) }
        s.fieldDefs.forEach { new(it.id) }
        return HouseSnapshot(
            house = s.house.copy(id = houseId),
            containers = s.containers.map { it.copy(id = ids.getValue(it.id), houseId = houseId, parentId = mapped(it.parentId)) },
            items = s.items.map { it.copy(id = ids.getValue(it.id), houseId = houseId, containerId = mapped(it.containerId)) },
            categories = s.categories.map { it.copy(id = ids.getValue(it.id), houseId = houseId, parentId = mapped(it.parentId)) },
            itemCategories = s.itemCategories.map { it.copy(id = newId(), houseId = houseId, itemId = mapped(it.itemId)!!, categoryId = mapped(it.categoryId)!!) },
            tags = s.tags.map { it.copy(id = ids.getValue(it.id), houseId = houseId) },
            itemTags = s.itemTags.map { it.copy(id = newId(), houseId = houseId, itemId = mapped(it.itemId)!!, tagId = mapped(it.tagId)!!) },
            learned = s.learned.map { it.copy(houseId = houseId, categoryId = mapped(it.categoryId)!!) },
            trash = s.trash.map { it.copy(id = newId(), houseId = houseId, targetId = mapped(it.targetId)!!, parentId = mapped(it.parentId)) },
            photos = s.photos.map { it.copy(id = newId(), houseId = houseId, ownerId = if (it.ownerType == Photo.OWNER_HOUSE) houseId else mapped(it.ownerId)!!) },
            fieldDefs = s.fieldDefs.map { it.copy(id = ids.getValue(it.id), houseId = houseId, itemId = mapped(it.itemId)) },
            fieldValues = s.fieldValues.map { it.copy(id = newId(), houseId = houseId, itemId = mapped(it.itemId)!!, fieldId = mapped(it.fieldId)!!) },
        )
    }
}
