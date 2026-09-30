package app.trecos.sync

import app.trecos.backup.SnapshotFormat
import app.trecos.backup.SnapshotFormatException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The Trecos folder in Drive is gone, or holds files the app can't read (spec "Folder changed outside the app"). */
class DriveFolderMissingException(message: String) : Exception(message)

/** Sync data in a format newer than this app understands (spec "Format compatibility"). */
class NewerFormatException(val version: Int) : Exception("Sync format $version is newer than ${SnapshotFormat.VERSION}")

/**
 * The shared store behind sync, laid out as in design D14:
 * `houses/<houseId>/commits/<id>.jsonl.gz`, `houses/<houseId>/refs/<deviceId>.json`
 * and `objects/<sha256>.webp`. Drive implements it; tests use an in-memory one.
 */
interface SyncRemote {
    /**
     * Makes sure the Trecos folder exists, creating it on the first connection.
     *
     * @param create whether to create it when it is missing.
     * @return `true` when it existed already.
     * @throws DriveFolderMissingException when it is missing and [create] is `false`.
     */
    suspend fun open(create: Boolean): Boolean

    /** @return the ids of the houses with sync data. */
    suspend fun houses(): List<String>

    /**
     * @param houseId the house.
     * @return each device's ref.
     */
    suspend fun refs(houseId: String): Map<String, Ref>

    /**
     * Writes this device's ref.
     *
     * @param houseId the house.
     * @param deviceId this device.
     * @param ref the ref.
     */
    suspend fun putRef(houseId: String, deviceId: String, ref: Ref)

    /**
     * @param houseId the house.
     * @param commitId the commit.
     * @return its file, or `null` when it doesn't exist.
     */
    suspend fun commit(houseId: String, commitId: String): ByteArray?

    /**
     * Writes a commit; writing one that exists already does nothing.
     *
     * @param houseId the house.
     * @param commitId the commit.
     * @param bytes its file.
     */
    suspend fun putCommit(houseId: String, commitId: String, bytes: ByteArray)

    /**
     * @param houseId the house.
     * @return the ids of every commit of the house.
     */
    suspend fun commits(houseId: String): List<String>

    /**
     * Deletes a commit.
     *
     * @param houseId the house.
     * @param commitId the commit.
     */
    suspend fun deleteCommit(houseId: String, commitId: String)

    /** @return the hashes of every stored photo. */
    suspend fun objects(): Set<String>

    /**
     * Stores a photo once, by its hash.
     *
     * @param sha256 the hash.
     * @param bytes the image.
     */
    suspend fun putObject(sha256: String, bytes: ByteArray)

    /**
     * @param sha256 the hash.
     * @return the image, or `null`.
     */
    suspend fun getObject(sha256: String): ByteArray?
}

/** Reads and writes commit files: gzip, a metadata line, then the rows as in the snapshot format. */
object CommitCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * @param parents the parent commits.
     * @param deviceId the device.
     * @param rows the content.
     * @return the commit id: the same inputs always give the same id.
     */
    fun commitId(parents: List<String>, deviceId: String, rows: Rows): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parents.forEach { digest.update(it.toByteArray()); digest.update(0) }
        digest.update(deviceId.toByteArray())
        rows.toSortedMap().forEach { (key, row) ->
            digest.update(key.toByteArray())
            digest.update(row.toString().toByteArray())
        }
        return digest.digest().take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * @param meta the commit's metadata.
     * @param rows its content.
     * @return the file.
     */
    fun encode(meta: CommitMeta, rows: Rows): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).bufferedWriter().use { writer ->
            writer.write(buildJsonObject { put("commit", json.encodeToJsonElement(CommitMeta.serializer(), meta)) }.toString())
            writer.write("\n")
            rows.toSortedMap().forEach { (key, row) ->
                writer.write(buildJsonObject { put("t", key.substringBefore('/')); put("r", row) }.toString())
                writer.write("\n")
            }
        }
        return out.toByteArray()
    }

    /**
     * @param bytes a commit file.
     * @return its metadata and rows.
     * @throws NewerFormatException when it was written in a newer format.
     * @throws SnapshotFormatException when it can't be read.
     */
    fun decode(bytes: ByteArray): Pair<CommitMeta, Rows> {
        val lines = try {
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().readLines().filter(String::isNotBlank)
        } catch (e: Exception) {
            throw SnapshotFormatException("Not a commit file", e)
        }
        val meta = try {
            json.decodeFromJsonElement(CommitMeta.serializer(), json.parseToJsonElement(lines.first()).jsonObject.getValue("commit"))
        } catch (e: Exception) {
            throw SnapshotFormatException("Bad commit header", e)
        }
        if (meta.formatVersion > SnapshotFormat.VERSION) throw NewerFormatException(meta.formatVersion)
        val snapshotText = lines.drop(1).joinToString("\n")
        val snapshot = SnapshotFormat.read(snapshotText.reader().buffered())
        return meta to SnapshotFormat.toRows(snapshot)
    }

    /**
     * @param bytes a commit file.
     * @return only its metadata.
     */
    fun meta(bytes: ByteArray): CommitMeta {
        val first = GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readLine() }
            ?: throw SnapshotFormatException("Empty commit")
        return json.decodeFromJsonElement(CommitMeta.serializer(), json.parseToJsonElement(first).jsonObject.getValue("commit"))
    }

    /** @return a JSON row's string value of a field, or `null`. */
    fun JsonObject.string(field: String): String? = this[field]?.jsonPrimitive?.let { if (it.isString) it.content else null }
}
