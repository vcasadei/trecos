package app.trecos.ui.places

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens the camera or the gallery. Picked images arrive through the callback
 * given to [PhotoSource.rememberLaunchers].
 *
 * @property camera opens the phone's camera app for one photo.
 * @property gallery opens the Android photo picker for at most the given number of photos.
 */
class PhotoLaunchers(val camera: () -> Unit, val gallery: (max: Int) -> Unit)

/** Where photos come from; replaced by a fake in tests, which can't drive the system picker. */
interface PhotoSource {
    /**
     * Creates the launchers for one screen.
     *
     * @param onPicked called with the picked or captured images; empty when cancelled.
     * @return the launchers.
     */
    @Composable
    fun rememberLaunchers(onPicked: (List<Uri>) -> Unit): PhotoLaunchers
}

/**
 * The real source: the camera app through a [FileProvider] URI in the app's
 * cache (so nothing reaches the gallery) and the Android photo picker, with no
 * permission requested.
 */
object SystemPhotoSource : PhotoSource {

    /** The folder in the cache the camera app writes into. */
    fun cameraDir(context: Context): File = File(context.cacheDir, "camera").apply { mkdirs() }

    @Composable
    override fun rememberLaunchers(onPicked: (List<Uri>) -> Unit): PhotoLaunchers {
        val context = LocalContext.current
        val target = remember { arrayOf<Uri?>(null) }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            onPicked(if (saved) listOfNotNull(target[0]) else emptyList())
        }
        val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            onPicked(listOfNotNull(uri))
        }
        val multiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)) { uris ->
            onPicked(uris)
        }
        return remember {
            PhotoLaunchers(
                camera = {
                    val file = File.createTempFile("capture", ".jpg", cameraDir(context))
                    val uri = FileProvider.getUriForFile(context, "app.trecos.fileprovider", file)
                    target[0] = uri
                    camera.launch(uri)
                },
                gallery = { max ->
                    val request = PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        .setMaxItems(max.coerceAtLeast(2))
                        .build()
                    if (max <= 1) single.launch(request) else multiple.launch(request)
                },
            )
        }
    }
}

/** The photo source in use; tests provide a fake. */
val LocalPhotoSource = staticCompositionLocalOf<PhotoSource> { SystemPhotoSource }
