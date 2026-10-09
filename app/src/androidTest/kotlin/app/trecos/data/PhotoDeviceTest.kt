package app.trecos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.trecos.places.PhotoStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The photo pipeline on a real Android image: decode, resize, WebP without
 * metadata, thumbnail.
 */
@RunWith(AndroidJUnit4::class)
class PhotoDeviceTest {

    @Test
    fun aCameraPhotoIsStoredSmallAndWithoutLocation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "photo-device-test").apply { deleteRecursively(); mkdirs() }
        val jpeg = File(dir, "camera.jpg")
        Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(10, 120, 200)) }
            .compress(Bitmap.CompressFormat.JPEG, 90, jpeg.outputStream())
        ExifInterface(jpeg.path).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "23/1,33/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S")
            saveAttributes()
        }
        val store = PhotoStore(File(dir, "files"), context.contentResolver)

        val sha = store.import(Uri.fromFile(jpeg))!!

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(store.photoFile(sha).path, bounds)
        assertEquals(1920, bounds.outWidth)
        assertEquals("image/webp", bounds.outMimeType)
        assertNull(ExifInterface(store.photoFile(sha).path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        val thumb = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(store.thumbnail(sha)!!.path, thumb)
        assertEquals(PhotoStore.THUMB_EDGE, thumb.outWidth)
    }
}
