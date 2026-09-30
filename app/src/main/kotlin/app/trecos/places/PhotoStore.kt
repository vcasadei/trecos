package app.trecos.places

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores photos small and private (design D8): each image is decoded once to
 * at most [MAX_EDGE] pixels on its long side with orientation corrected,
 * re-encoded as WebP (which drops every piece of metadata, location
 * included), and named by its SHA-256 so identical photos are stored once. A
 * square thumbnail is written next to it. The original is never kept.
 *
 * @param root the app's private files directory.
 * @param resolver reads picked and captured images.
 */
class PhotoStore(root: File, private val resolver: ContentResolver) {

    private val photos = File(root, "photos")
    private val thumbs = File(root, "thumbs")

    /**
     * Imports one image.
     *
     * @param uri a picked or captured image.
     * @return the stored photo's SHA-256, or `null` if the image couldn't be decoded.
     */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = requireNotNull(resolver.openInputStream(uri)) { "Can't open $uri" }.use { it.readBytes() }
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val longEdge = maxOf(info.size.width, info.size.height)
                if (longEdge > MAX_EDGE) {
                    val scale = MAX_EDGE.toFloat() / longEdge
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            store(bitmap).also { bitmap.recycle() }
        }.getOrNull()
    }

    /**
     * Writes a decoded image and its thumbnail.
     *
     * @param bitmap the image, already at its stored size.
     * @return its SHA-256.
     */
    private fun store(bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(webp(), QUALITY, out)
            out.toByteArray()
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        photos.mkdirs()
        val file = photoFile(sha)
        if (!file.exists()) file.writeBytes(bytes)
        writeThumbnail(sha, bitmap)
        return sha
    }

    /**
     * @param sha a stored photo's SHA-256.
     * @return its full-size file.
     */
    fun photoFile(sha: String): File = File(photos, "$sha.webp")

    /**
     * Returns a photo's thumbnail, regenerating it from the stored photo when
     * it is missing (thumbnails are never synced).
     *
     * @param sha a stored photo's SHA-256.
     * @return the thumbnail file, or `null` if the photo itself is missing.
     */
    suspend fun thumbnail(sha: String): File? = withContext(Dispatchers.IO) {
        val thumb = File(thumbs, "$sha.webp")
        if (thumb.exists()) return@withContext thumb
        val source = photoFile(sha).takeIf { it.exists() } ?: return@withContext null
        val bitmap = BitmapFactory.decodeFile(source.path) ?: return@withContext null
        writeThumbnail(sha, bitmap)
        bitmap.recycle()
        thumb
    }

    /**
     * Writes a centred square thumbnail.
     *
     * @param sha the photo's SHA-256.
     * @param bitmap the full image.
     */
    private fun writeThumbnail(sha: String, bitmap: Bitmap) {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, THUMB_EDGE, THUMB_EDGE, true)
        thumbs.mkdirs()
        File(thumbs, "$sha.webp").outputStream().use { scaled.compress(webp(), QUALITY, it) }
        if (scaled !== square) scaled.recycle()
        if (square !== bitmap) square.recycle()
    }

    /**
     * Deletes stored photos and thumbnails no row refers to any more.
     *
     * @param referenced every SHA-256 still used by a photo row.
     * @param olderThan only files last written before this time (epoch ms) are deleted, so photos
     *   just added in a form that isn't saved yet are kept; everything by default.
     * @return how many photos were deleted.
     */
    suspend fun deleteUnreferenced(referenced: Set<String>, olderThan: Long = Long.MAX_VALUE): Int = withContext(Dispatchers.IO) {
        val orphans = photos.listFiles().orEmpty().filter { it.nameWithoutExtension !in referenced && it.lastModified() < olderThan }
        orphans.forEach { File(thumbs, it.name).delete(); it.delete() }
        thumbs.listFiles().orEmpty().filter { it.nameWithoutExtension !in referenced }.forEach { it.delete() }
        orphans.size
    }

    /** @return the WebP format for the running Android version. */
    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    companion object {
        /** The longest side of a stored photo, in pixels. */
        const val MAX_EDGE = 1920

        /** The side of a square thumbnail, in pixels. */
        const val THUMB_EDGE = 320

        /** The WebP quality. */
        const val QUALITY = 80
    }
}
