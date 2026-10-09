package app.trecos.backup

import app.trecos.data.TrecosDatabase
import app.trecos.places.PhotoStore
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The first entry of a backup (design D15).
 *
 * @property formatVersion the [SnapshotFormat.VERSION] that wrote it.
 * @property createdAt when it was written, epoch milliseconds.
 * @property appVersion the app version that wrote it.
 * @property houses what the backup holds, for the import preview.
 */
@Serializable
data class Manifest(
    val formatVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    val houses: List<ManifestHouse>,
)

/**
 * One house of a backup, as the preview shows it.
 *
 * @property id the house id.
 * @property name the house name.
 * @property containers how many containers it has (not counting the trash).
 * @property items how many items it has (not counting the trash).
 * @property photos how many photos it has.
 */
@Serializable
data class ManifestHouse(val id: String, val name: String, val containers: Int, val items: Int, val photos: Int)

/** Where each part of a backup lives inside the zip. */
object BackupLayout {
    /** The manifest entry. */
    const val MANIFEST = "manifest.json"

    /**
     * @param houseId a house.
     * @return its snapshot entry.
     */
    fun snapshot(houseId: String) = "houses/$houseId/snapshot.jsonl"

    /**
     * @param sha256 a photo's hash.
     * @return its full-size image entry.
     */
    fun photo(sha256: String) = "objects/$sha256.webp"

    /** The JSON settings of manifests. */
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * @param now the current time.
     * @param zone the phone's time zone.
     * @return the suggested file name, such as `trecos-backup-2026-09-28.zip`.
     */
    fun fileName(now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "trecos-backup-${DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(now).atZone(zone))}.zip"
}

/**
 * Writes backups (spec "Export"): the manifest, one snapshot per house and
 * every full-size photo once. Thumbnails are left out and rebuilt on import.
 *
 * @param db the database.
 * @param photos the stored photos.
 */
class BackupExporter(private val db: TrecosDatabase, private val photos: PhotoStore) {

    /**
     * Writes the chosen houses to [out]. The caller removes the file if this throws.
     *
     * @param houseIds the houses to include, or empty for all.
     * @param out the destination; it is closed at the end.
     * @param now the current time.
     * @param appVersion the app version, recorded in the manifest.
     * @return the manifest written.
     * @throws java.io.IOException when writing fails, for example for lack of space.
     */
    suspend fun export(houseIds: Set<String>, out: OutputStream, now: Long, appVersion: String): Manifest = withContext(Dispatchers.IO) {
        val houses = db.snapshots().houses().filter { houseIds.isEmpty() || it.id in houseIds }
        val snapshots = houses.map { HouseSnapshot.read(db, it) }
        val manifest = Manifest(
            formatVersion = SnapshotFormat.VERSION,
            createdAt = now,
            appVersion = appVersion,
            houses = snapshots.map { s ->
                ManifestHouse(
                    s.house.id, s.house.name,
                    containers = s.containers.count { it.deletedAt == null },
                    items = s.items.count { it.deletedAt == null },
                    photos = s.photos.size,
                )
            },
        )
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupLayout.MANIFEST))
            zip.write(BackupLayout.json.encodeToString(Manifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            for (snapshot in snapshots) {
                zip.putNextEntry(ZipEntry(BackupLayout.snapshot(snapshot.house.id)))
                val writer = OutputStreamWriter(zip, Charsets.UTF_8)
                SnapshotFormat.write(snapshot, writer)
                zip.closeEntry()
            }
            for (sha in snapshots.flatMap { it.photos }.map { it.sha256 }.distinct().sorted()) {
                val file = photos.photoFile(sha).takeIf { it.exists() } ?: continue
                zip.putNextEntry(ZipEntry(BackupLayout.photo(sha)))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        manifest
    }
}
