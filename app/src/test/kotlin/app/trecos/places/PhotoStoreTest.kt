package app.trecos.places

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scenarios "Photo from a 12-megapixel camera" and "Unreadable file", plus
 * thumbnails and file clean-up (task 6.2).
 */
@RunWith(RobolectricTestRunner::class)
class PhotoStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store by lazy { PhotoStore(folder.newFolder("files"), context.contentResolver) }

    /**
     * Writes a JPEG with location data in its EXIF.
     *
     * @param width the width in pixels.
     * @param height the height in pixels.
     * @return the file.
     */
    private fun cameraJpeg(width: Int, height: Int): File {
        val file = folder.newFile("camera-$width.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 140, 200)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "23/1,33/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "46/1,38/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
            setAttribute(ExifInterface.TAG_MAKE, "TestCam")
            saveAttributes()
        }
        assertNotNull(ExifInterface(file.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        return file
    }

    @Test
    fun photoFromA12MegapixelCamera() = runBlocking {
        val sha = store.import(Uri.fromFile(cameraJpeg(4000, 3000)))!!

        val stored = store.photoFile(sha)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(stored.path, bounds)
        assertEquals(1920, maxOf(bounds.outWidth, bounds.outHeight))
        assertEquals(1440, minOf(bounds.outWidth, bounds.outHeight))
        assertEquals("image/webp", bounds.outMimeType)
        val exif = ExifInterface(stored.path)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals(64, sha.length)
    }

    @Test
    fun smallPhotosAreNotEnlarged() = runBlocking {
        val sha = store.import(Uri.fromFile(cameraJpeg(800, 600)))!!
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(store.photoFile(sha).path, bounds)
        assertEquals(800, bounds.outWidth)
    }

    @Test
    fun unreadableFile() = runBlocking {
        val broken = folder.newFile("broken.jpg").apply { writeText("not an image") }
        assertNull(store.import(Uri.fromFile(broken)))
        assertNotNull(store.import(Uri.fromFile(cameraJpeg(640, 480))))
    }

    @Test
    fun theSamePhotoIsStoredOnce() = runBlocking {
        val file = cameraJpeg(1000, 800)
        assertEquals(store.import(Uri.fromFile(file)), store.import(Uri.fromFile(file)))
    }

    @Test
    fun thumbnailRegenerated() = runBlocking {
        val sha = store.import(Uri.fromFile(cameraJpeg(2000, 1000)))!!
        val thumb = store.thumbnail(sha)!!
        assertTrue(thumb.delete())

        val again = store.thumbnail(sha)!!

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(again.path, bounds)
        assertEquals(PhotoStore.THUMB_EDGE, bounds.outWidth)
        assertEquals(PhotoStore.THUMB_EDGE, bounds.outHeight)
    }

    @Test
    fun unreferencedFilesAreDeleted() = runBlocking {
        val kept = store.import(Uri.fromFile(cameraJpeg(640, 480)))!!
        val orphan = store.import(Uri.fromFile(cameraJpeg(700, 500)))!!

        assertEquals(1, store.deleteUnreferenced(setOf(kept)))

        assertTrue(store.photoFile(kept).exists())
        assertTrue(!store.photoFile(orphan).exists())
    }
}
