package app.trecos.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks on a device that the database runs on SQLCipher's engine and that,
 * without a key, the file is plain SQLite (design D3).
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TrecosDatabase

    @Before
    fun open() {
        context.deleteDatabase(TrecosDatabase.FILE_NAME)
        db = TrecosDatabase.open(context)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(TrecosDatabase.FILE_NAME)
    }

    @Test
    fun runsOnSqlCipherWithoutAKey() = runBlocking {
        db.houses().insert(House(id = "h1", name = "Apartment", icon = "house", createdAt = 1, updatedAt = 1))
        assertEquals("Apartment", db.houses().get("h1")?.name)

        val version = db.openHelper.writableDatabase.query("PRAGMA cipher_version").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        assertTrue("cipher_version is $version", !version.isNullOrBlank())

        val header = context.getDatabasePath(TrecosDatabase.FILE_NAME).inputStream().use { it.readNBytes(15) }
        assertEquals("SQLite format 3", String(header))
    }

    @Test
    fun fullTextSearchIgnoresAccentsOnTheSqlCipherEngine() = runBlocking {
        db.houses().insert(House(id = "h1", name = "Apartment", icon = "house", createdAt = 1, updatedAt = 1))
        db.items().insert(Item(id = "i1", houseId = "h1", name = "Cabeça de impressão", serial = "SN4478X21", createdAt = 1, updatedAt = 1))
        assertEquals(listOf("i1"), db.search().matches("cabeca*").map { it.refId })
        assertEquals(listOf("i1"), db.search().matches("name:sn4478*").map { it.refId })
    }

    @Test
    fun customTextValuesAreSearchedOnTheSqlCipherEngine() = runBlocking {
        db.houses().insert(House(id = "h1", name = "Apartment", icon = "house", createdAt = 1, updatedAt = 1))
        db.items().insert(Item(id = "i1", houseId = "h1", name = "3D printer", createdAt = 1, updatedAt = 1))
        db.fields().insertDef(FieldDef("f1", "h1", "i1", "Firmware", "Text", null, 0, 1, 1))
        db.fields().insertValue(FieldValue("v1", "h1", "i1", "f1", "Klipper", 1, 1))
        assertEquals(listOf("i1"), db.search().matches("description:klipper*").map { it.refId })
        db.fields().deleteValue("v1")
        assertEquals(emptyList<String>(), db.search().matches("klipper*").map { it.refId })
    }
}
