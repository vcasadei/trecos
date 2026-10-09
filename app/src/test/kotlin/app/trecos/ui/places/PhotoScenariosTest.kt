package app.trecos.ui.places

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.geometry.Offset
import app.trecos.data.AddFlow
import app.trecos.data.Fixtures.item
import app.trecos.data.ImageSource
import app.trecos.data.Photo
import app.trecos.ui.shell.TrecosApp
import app.trecos.ui.theme.TrecosTheme
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element

/**
 * Scenarios of the photos spec (tasks 6.3-6.8). The system camera and photo
 * picker can't run in tests, so a fake [PhotoSource] records what was asked
 * and returns real JPEG files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class PhotoScenariosTest : PlacesTestBase() {

    /** A fake source: remembers the requests and returns [next] when launched. */
    private inner class FakeSource : PhotoSource {
        val galleryMax = mutableListOf<Int>()
        var cameraCalls = 0
        var next: List<Uri> = emptyList()

        @Composable
        override fun rememberLaunchers(onPicked: (List<Uri>) -> Unit): PhotoLaunchers = remember {
            PhotoLaunchers(
                camera = {
                    cameraCalls++
                    onPicked(next.take(1))
                },
                gallery = { max ->
                    galleryMax += max
                    onPicked(next.take(max))
                },
            )
        }
    }

    private val fake = FakeSource()

    @Before
    fun useFakeSource() {
        rule.runOnUiThread {
            rule.activity.setContent {
                CompositionLocalProvider(LocalPhotoSource provides fake) { TrecosTheme { TrecosApp() } }
            }
        }
    }

    /**
     * Writes a JPEG.
     *
     * @param shade makes each image distinct, so each gets its own SHA-256.
     * @return its URI.
     */
    private fun jpeg(shade: Int): Uri {
        val file = File.createTempFile("pic$shade", ".jpg", app.cameraDir.parentFile)
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(shade, 100, 150)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    /**
     * Seeds an item with photos stored through the real store.
     *
     * @param count how many photos.
     * @return their SHA-256s, main first.
     */
    private fun itemWithPhotos(count: Int): List<String> {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        return runBlocking {
            val shas = (1..count).map { app.photoStore.import(jpeg(40 * it))!! }
            app.database.photos().insert(shas.mapIndexed { i, sha -> Photo("p$i", "h1", Photo.OWNER_ITEM, "pi", sha, i, 1) })
            shas
        }
    }

    /** Opens the item's edit form. */
    private fun editPi() {
        click(rowTag("pi"))
        clickDescription("Edit")
    }

    /** Opens the new-item form on the top level. */
    private fun newItem() {
        click(ADD_BUTTON_TAG)
        text("Item").performClick()
        rule.waitForIdle()
    }

    @Test
    fun noCameraOrStoragePermissionIsDeclared() {
        val config = Properties().apply {
            PhotoScenariosTest::class.java.getResourceAsStream("/com/android/tools/test_config.properties").use(::load)
        }
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File(config.getProperty("android_merged_manifest")))
        val permissions = manifest.getElementsByTagName("uses-permission").let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS("http://schemas.android.com/apk/res/android", "name") }
        }
        val forbidden = listOf("CAMERA", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VISUAL_USER_SELECTED")
        assertTrue("declared: $permissions", permissions.none { p -> forbidden.any { p.endsWith(".$it") } })
    }

    @Test
    fun pickingFromTheGallery() {
        itemWithPhotos(1)
        runBlocking { app.preferences.setImageSource(ImageSource.Gallery) }
        fake.next = listOf(jpeg(10), jpeg(20), jpeg(30))
        editPi()
        rule.waitUntil(10_000) { rule.onAllNodes(isPhotoThumb, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        click("add_photo")

        assertEquals(listOf(2), fake.galleryMax)
        rule.waitUntil(10_000) { rule.onAllNodes(isPhotoThumb, useUnmergedTree = true).fetchSemanticsNodes().size == 3 }
    }

    @Test
    fun limitReached() {
        itemWithPhotos(3)
        editPi()

        tag("photo_limit").assertIsDisplayed()
        tag("add_photo").assertIsNotEnabled()
    }

    @Test
    fun remembering() {
        seed()
        fake.next = listOf(jpeg(60))
        newItem()
        click("add_photo")
        click("source_camera")

        assertEquals(1, fake.cameraCalls)
        rule.waitUntil(10_000) { runBlocking { app.preferences.imageSource.first() } == ImageSource.Camera }
        click("add_photo")
        assertEquals(2, fake.cameraCalls)
    }

    @Test
    fun keepAsking() {
        seed()
        fake.next = listOf(jpeg(70))
        newItem()
        click("add_photo")
        click("remember_choice")
        click("source_gallery")

        assertEquals(1, fake.galleryMax.size)
        assertEquals(ImageSource.Ask, runBlocking { app.preferences.imageSource.first() })
        click("add_photo")
        tag("source_gallery").assertIsDisplayed()
    }

    @Test
    fun settingTheMainPhoto() {
        val shas = itemWithPhotos(3)
        editPi()
        click(photoTag(shas[2]))
        click("photo_main_${shas[2]}")
        saveAndClose()

        rule.waitUntil(10_000) { runBlocking { app.database.photos().forOwner("pi").firstOrNull()?.sha256 } == shas[2] }
        pressBack()
        tag(photoTag(shas[2])).assertIsDisplayed()
    }

    @Test
    fun openingAPhotoAndZoomingIntoASerialLabel() {
        val shas = itemWithPhotos(1)
        click(rowTag("pi"))
        click("carousel_${shas[0]}")

        tag("photo_viewer").assertIsDisplayed()
        val viewer = tag("viewer_${shas[0]}")
        viewer.performTouchInput { pinch(center - Offset(20f, 0f), center - Offset(200f, 0f), center + Offset(20f, 0f), center + Offset(200f, 0f)) }
        rule.waitForIdle()

        val zoom = viewer.fetchSemanticsNode().config[ZoomLevel]
        assertTrue("zoom $zoom", zoom > 1f)
    }

    @Test
    fun thumbnailRegenerated() {
        val shas = itemWithPhotos(1)
        File(app.photoStore.photoFile(shas[0]).parentFile!!.parentFile, "thumbs").deleteRecursively()

        tag(photoTag(shas[0])).assertIsDisplayed()
        rule.waitUntil(10_000) { File(app.photoStore.photoFile(shas[0]).parentFile!!.parentFile, "thumbs/${shas[0]}.webp").exists() }
    }

    @Test
    fun photoFirst() {
        seed()
        runBlocking {
            app.preferences.setAddFlow(AddFlow.PhotoFirst)
            app.preferences.setImageSource(ImageSource.Camera)
        }
        fake.next = listOf(jpeg(90))
        newItem()

        assertEquals(1, fake.cameraCalls)
        rule.waitUntil(10_000) { rule.onAllNodes(isPhotoThumb, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        tag(fieldTag("name")).assertIsFocused()
    }

    @Test
    fun photoCancelled() {
        seed()
        runBlocking {
            app.preferences.setAddFlow(AddFlow.PhotoFirst)
            app.preferences.setImageSource(ImageSource.Camera)
        }
        fake.next = emptyList()
        newItem()

        assertEquals(1, fake.cameraCalls)
        tag(fieldTag("name")).assertIsDisplayed()
        assertTrue(rule.onAllNodes(isPhotoThumb, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
