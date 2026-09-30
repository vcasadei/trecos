package app.trecos.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A file in Drive, with only the fields Trecos asks for.
 *
 * @property id the Drive file id.
 * @property name the file name.
 * @property mimeType its type; folders are [DriveClient.FOLDER].
 * @property parents the folders it is in.
 * @property modifiedTime when it last changed, RFC 3339.
 * @property size the size in bytes, as Drive reports it (absent for folders).
 */
@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String = "",
    val parents: List<String> = emptyList(),
    val modifiedTime: String? = null,
    val size: String? = null,
)

/**
 * The signed-in Google user, from Drive's `about`.
 *
 * @property displayName the name.
 * @property emailAddress the e-mail address.
 */
@Serializable
data class DriveUser(val displayName: String = "", val emailAddress: String = "")

/** A Drive request that failed for good. */
open class DriveException(val code: Int, message: String) : IOException(message)

/** The access token was refused; sign in again. */
class DriveAuthException(message: String) : DriveException(401, message)

/** The file doesn't exist (any more). */
class DriveNotFoundException(message: String) : DriveException(404, message)

/** The Drive operations sync needs; [DriveClient] is the real one, tests use an in-memory one. */
interface DriveFiles {
    /** @see DriveClient.list */
    suspend fun list(query: String, space: String = "drive"): List<DriveFile>

    /** @see DriveClient.createFolder */
    suspend fun createFolder(name: String, parent: String?): DriveFile

    /** @see DriveClient.upload */
    suspend fun upload(name: String, parent: String, bytes: ByteArray, mimeType: String = "application/octet-stream"): DriveFile

    /** @see DriveClient.update */
    suspend fun update(id: String, bytes: ByteArray, mimeType: String = "application/octet-stream")

    /** @see DriveClient.download */
    suspend fun download(id: String): ByteArray

    /** @see DriveClient.delete */
    suspend fun delete(id: String)
}

/**
 * Drive REST v3 over `HttpsURLConnection` (design D14): no Google client
 * library. Failed requests that may succeed later (429, 5xx, network errors)
 * are retried with exponential backoff.
 *
 * @param token returns a current OAuth access token.
 * @param baseUrl the API host; tests point it at a local server.
 * @param attempts how many times a request is tried in all.
 * @param backoffMs the first wait between tries, doubled each time.
 * @param sleep waits between tries; tests make it instant.
 */
class DriveClient(
    private val token: suspend () -> String,
    private val baseUrl: String = "https://www.googleapis.com",
    private val attempts: Int = 5,
    private val backoffMs: Long = 1_000,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : DriveFiles {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)

    @Serializable
    private data class About(val user: DriveUser = DriveUser())

    /**
     * Lists files matching a Drive query, all pages.
     *
     * @param query a Drive `q` expression, such as `'<folder>' in parents and trashed = false`.
     * @param space `drive` or `appDataFolder`.
     * @return the files.
     */
    override suspend fun list(query: String, space: String): List<DriveFile> {
        val files = ArrayList<DriveFile>()
        var page: String? = null
        do {
            val url = "$baseUrl/drive/v3/files?spaces=$space&pageSize=1000&q=${enc(query)}" +
                "&fields=${enc("nextPageToken,files($FIELDS)")}" + (page?.let { "&pageToken=${enc(it)}" } ?: "")
            val list = json.decodeFromString(FileList.serializer(), request("GET", url).decodeToString())
            files += list.files
            page = list.nextPageToken
        } while (page != null)
        return files
    }

    /**
     * Creates a folder.
     *
     * @param name its name.
     * @param parent the folder it goes in, or `null` for My Drive.
     * @return the folder.
     */
    override suspend fun createFolder(name: String, parent: String?): DriveFile {
        val metadata = metadata(name, parent, FOLDER)
        val body = request("POST", "$baseUrl/drive/v3/files?fields=${enc(FIELDS)}", metadata.toByteArray(), "application/json; charset=UTF-8")
        return json.decodeFromString(DriveFile.serializer(), body.decodeToString())
    }

    /**
     * Uploads a new file in one request.
     *
     * @param name its name.
     * @param parent the folder it goes in (`appDataFolder` for the hidden app folder).
     * @param bytes the content.
     * @param mimeType the content type.
     * @return the file.
     */
    override suspend fun upload(name: String, parent: String, bytes: ByteArray, mimeType: String): DriveFile {
        val boundary = "trecos-${System.nanoTime()}"
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata(name, parent, mimeType))
            append("\r\n--$boundary\r\nContent-Type: $mimeType\r\n\r\n")
        }.toByteArray() + bytes + "\r\n--$boundary--\r\n".toByteArray()
        val result = request("POST", "$baseUrl/upload/drive/v3/files?uploadType=multipart&fields=${enc(FIELDS)}", body, "multipart/related; boundary=$boundary")
        return json.decodeFromString(DriveFile.serializer(), result.decodeToString())
    }

    /**
     * Replaces a file's content.
     *
     * @param id the file.
     * @param bytes the new content.
     * @param mimeType the content type.
     */
    override suspend fun update(id: String, bytes: ByteArray, mimeType: String) {
        request("PATCH", "$baseUrl/upload/drive/v3/files/${enc(id)}?uploadType=media", bytes, mimeType)
    }

    /**
     * @param id the file.
     * @return its content.
     */
    override suspend fun download(id: String): ByteArray = request("GET", "$baseUrl/drive/v3/files/${enc(id)}?alt=media")

    /** @param id the file to delete for good. */
    override suspend fun delete(id: String) {
        try {
            request("DELETE", "$baseUrl/drive/v3/files/${enc(id)}")
        } catch (_: DriveNotFoundException) {
            // Already gone: deleting is idempotent.
        }
    }

    /** @return the signed-in user's name and e-mail address. */
    suspend fun user(): DriveUser =
        json.decodeFromString(About.serializer(), request("GET", "$baseUrl/drive/v3/about?fields=${enc("user(displayName,emailAddress)")}").decodeToString()).user

    /** Builds the JSON metadata of a new file. */
    private fun metadata(name: String, parent: String?, mimeType: String): String =
        json.encodeToString(NewFile.serializer(), NewFile(name, mimeType, listOfNotNull(parent)))

    @Serializable
    private data class NewFile(val name: String, val mimeType: String, val parents: List<String>)

    /** Sends a request, retrying what may succeed later. */
    private suspend fun request(method: String, url: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        var wait = backoffMs
        var last: IOException? = null
        repeat(attempts) { attempt ->
            try {
                return send(method, url, body, contentType)
            } catch (e: DriveException) {
                if (e.code != 429 && e.code < 500) throw e
                last = e
            } catch (e: IOException) {
                last = e
            }
            if (attempt < attempts - 1) {
                sleep(wait)
                wait *= 2
            }
        }
        throw last ?: IOException("Request failed")
    }

    /** Sends one request. */
    private suspend fun send(method: String, url: String, body: ByteArray?, contentType: String?): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            // HttpURLConnection has no PATCH; Google APIs accept the override header.
            connection.requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            connection.setRequestProperty("Authorization", "Bearer ${token()}")
            if (body != null) {
                connection.doOutput = true
                contentType?.let { connection.setRequestProperty("Content-Type", it) }
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.use { it.readBytes() }
            } else {
                val detail = connection.errorStream?.use { it.readBytes().decodeToString() }.orEmpty().take(300)
                throw when (code) {
                    401 -> DriveAuthException("Unauthorized: $detail")
                    404 -> DriveNotFoundException("Not found: $detail")
                    else -> DriveException(code, "HTTP $code: $detail")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** URL-encodes a query value. */
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        /** The type of Drive folders. */
        const val FOLDER = "application/vnd.google-apps.folder"

        private const val FIELDS = "id,name,mimeType,parents,modifiedTime,size"
    }
}
