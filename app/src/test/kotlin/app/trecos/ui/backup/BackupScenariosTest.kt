package app.trecos.ui.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import app.trecos.backup.BackupImporter
import app.trecos.backup.BackupLayout
import app.trecos.backup.Manifest
import app.trecos.backup.SnapshotFormat
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.house
import app.trecos.data.Fixtures.item
import app.trecos.data.Photo
import app.trecos.ui.places.PlacesTestBase
import app.trecos.ui.settings.SETTINGS_LIST_TAG
import app.trecos.ui.shell.TrecosApp
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.tabTag
import app.trecos.ui.theme.TrecosTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenarios of the backup spec (tasks 11.2 and 11.3). The system file picker
 * can't run in tests, so a fake hands out files in a temporary folder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class BackupScenariosTest : PlacesTestBase() {

    @get:Rule
    val folder = TemporaryFolder()

    /** Files in a temporary folder; can pretend the disk fills up. */
    private inner class FakeFiles : BackupFiles {
        var saveTo: File? = null
        var openFrom: File? = null
        var suggested: String? = null
        var spaceLeft = Long.MAX_VALUE
        val deleted = mutableListOf<Uri>()

        @Composable
        override fun rememberPickers(onCreated: (Uri?) -> Unit, onOpened: (Uri?) -> Unit): Pair<(String) -> Unit, () -> Unit> =
            { name: String ->
                suggested = name
                val file = saveTo ?: File(folder.root, name).also { saveTo = it }
                onCreated(Uri.fromFile(file))
            } to { onOpened(openFrom?.let(Uri::fromFile)) }

        override fun openOutput(context: Context, uri: Uri): OutputStream {
            val out = FileOutputStream(File(uri.path!!))
            return object : OutputStream() {
                var written = 0L
                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (written + len > spaceLeft) throw IOException("No space left on device")
                    written += len
                    out.write(b, off, len)
                }
                override fun close() = out.close()
            }
        }

        override fun openInput(context: Context, uri: Uri): InputStream = File(uri.path!!).inputStream()

        override fun delete(context: Context, uri: Uri) {
            deleted += uri
            File(uri.path!!).delete()
        }
    }

    private val files = FakeFiles()

    @Before
    fun useFakeFiles() {
        rule.runOnUiThread {
            rule.activity.setContent { CompositionLocalProvider(LocalBackupFiles provides files) { TrecosTheme { TrecosApp() } } }
        }
    }

    /** Opens Settings > Backup. */
    private fun openBackup() {
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_backup"))
        click("setting_backup")
    }

    /**
     * Stores a photo file and returns its hash.
     *
     * @param seed makes the bytes distinct.
     */
    private fun storedPhoto(seed: Int): String {
        val bytes = ByteArray(2_000) { (it * seed).toByte() }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        app.photoStore.photoFile(sha).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        return sha
    }

    /** Seeds a house with containers, items and a photo; returns the photo hash. */
    private fun seedHouse(items: Int = 3): String {
        seed(containers = listOf(container("boxA", name = "Box A")), items = (1..items).map { item("i$it", containerId = "boxA") })
        val sha = storedPhoto(7)
        runBlocking { app.database.photos().insert(listOf(Photo("p1", "h1", Photo.OWNER_ITEM, "i1", sha, 0, 1))) }
        return sha
    }

    /** Waits for an app-wide message. */
    private fun message(text: String) {
        rule.waitUntil(15_000) { rule.onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun exportingToDownloads() {
        val sha = seedHouse()
        openBackup()
        click("export")

        message("Backup saved.")
        assertEquals(BackupLayout.fileName(app.clock()), files.suggested)
        assertTrue(files.suggested!!.matches(Regex("trecos-backup-\\d{4}-\\d{2}-\\d{2}\\.zip")))
        val contents = runBlocking { BackupImporter(app.database, app.photoStore, folder.newFolder(), app.newId).read(files.saveTo!!.inputStream()) }
        assertEquals(listOf("Apartment"), contents.manifest.houses.map { it.name })
        assertEquals(3, contents.houses.single().items.size)
        assertTrue(File(contents.photoDir, "$sha.webp").exists())
    }

    @Test
    fun exportingOnlyChosenHouses() {
        seed(houses = listOf(house("h1", "Apartment"), house("h2", "Parents")))
        openBackup()
        click("export_house_h2")
        click("export")

        message("Backup saved.")
        val contents = runBlocking { BackupImporter(app.database, app.photoStore, folder.newFolder(), app.newId).read(files.saveTo!!.inputStream()) }
        assertEquals(listOf("h1"), contents.houses.map { it.house.id })
    }

    @Test
    fun notEnoughSpace() {
        seedHouse()
        files.spaceLeft = 100
        openBackup()
        click("export")

        message("couldn't be saved")
        assertEquals(1, files.deleted.size)
        assertFalse(files.saveTo!!.exists())
    }

    @Test
    fun addingHousesFromAnotherPhone() {
        seedHouse(items = 120)
        openBackup()
        click("export")
        message("Backup saved.")

        files.openFrom = files.saveTo
        click("import")
        tag("import_preview")
        text("Apartment: 1 container · 120 items · 1 photo")
        click("import_add")
        message("Backup imported.")

        val houses = runBlocking { app.database.houses().observeAll().first() }
        assertEquals(2, houses.size)
        val added = houses.single { it.id != "h1" }
        assertEquals(120, runBlocking { app.database.snapshots().items(added.id) }.size)
        assertEquals(120, runBlocking { app.database.snapshots().items("h1") }.size)
        val box = runBlocking { app.database.snapshots().containers(added.id) }.single()
        assertTrue(runBlocking { app.database.snapshots().items(added.id) }.all { it.containerId == box.id })
    }

    @Test
    fun replacingEverythingAfterConfirming() {
        seedHouse()
        openBackup()
        click("export")
        message("Backup saved.")
        runBlocking { app.database.items().insert(item("later")) }

        files.openFrom = files.saveTo
        click("import")
        click("import_replace")
        tag("replace_confirm")
        click("confirm_replace")
        message("Backup imported.")

        assertEquals(listOf("h1"), runBlocking { app.database.houses().observeAll().first() }.map { it.id })
        assertTrue(runBlocking { app.database.items().get("later") } == null)
        assertEquals(3, runBlocking { app.database.snapshots().items("h1") }.size)
    }

    @Test
    fun corruptFile() {
        seedHouse()
        val damaged = folder.newFile("damaged.zip").apply { writeBytes(ByteArray(500) { it.toByte() }) }
        files.openFrom = damaged
        openBackup()
        click("import")

        message("isn't a Trecos backup")
        assertEquals(1, runBlocking { app.database.houses().observeAll().first() }.size)
        assertEquals(3, runBlocking { app.database.snapshots().items("h1") }.size)
    }

    @Test
    fun backupFromANewerAppVersion() {
        seedHouse()
        val newer = folder.newFile("newer.zip")
        ZipOutputStream(newer.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupLayout.MANIFEST))
            val manifest = Manifest(SnapshotFormat.VERSION + 1, 0, "9.9", emptyList())
            zip.write(BackupLayout.json.encodeToString(Manifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
        }
        files.openFrom = newer
        openBackup()
        click("import")

        message("newer version of Trecos")
        assertEquals(3, runBlocking { app.database.snapshots().items("h1") }.size)
    }
}
