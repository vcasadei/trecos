package app.trecos.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.trecos.backup.BackupExporter
import app.trecos.backup.BackupImporter
import app.trecos.places.PhotoStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Export and import on the SQLCipher engine, with its search triggers (tasks 11.2, 11.3). */
@RunWith(AndroidJUnit4::class)
class BackupDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TrecosDatabase
    private val root by lazy { File(context.cacheDir, "backup-test").apply { deleteRecursively(); mkdirs() } }

    @Before
    fun open() {
        context.deleteDatabase(TrecosDatabase.FILE_NAME)
        db = TrecosDatabase.open(context)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(TrecosDatabase.FILE_NAME)
        root.deleteRecursively()
    }

    @Test
    fun exportThenAddAsNewHouses() = runBlocking {
        db.houses().insert(House(id = "h1", name = "Apartment", icon = "house", createdAt = 1, updatedAt = 1))
        db.containers().insert(Container(id = "c1", houseId = "h1", name = "Box A", icon = "box", createdAt = 1, updatedAt = 1))
        db.items().insert(Item(id = "i1", houseId = "h1", containerId = "c1", name = "Cabeça de impressão", createdAt = 1, updatedAt = 1))
        val photos = PhotoStore(File(root, "files"), context.contentResolver)
        val file = File(root, "backup.zip")

        BackupExporter(db, photos).export(emptySet(), file.outputStream(), 1, "test")
        val importer = BackupImporter(db, photos, File(root, "work"), { UUID.randomUUID().toString() })
        val added = importer.addAsNewHouses(importer.read(file.inputStream())).single()

        assertEquals(2, db.snapshots().houses().size)
        assertEquals(listOf("Cabeça de impressão"), db.snapshots().items(added).map { it.name })
        assertEquals(2, db.search().matches("cabeca*").size)
    }
}
