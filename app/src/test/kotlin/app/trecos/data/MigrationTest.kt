package app.trecos.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration tests against the exported schemas in `app/schemas/`. Every schema
 * change adds a test here that creates the previous version, fills it, runs the
 * migration and validates the result against the next exported schema.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TrecosDatabase::class.java)

    @Test
    fun version1To2KeepsItemsAndAddsCategoriesAndTags() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO house (id, name, icon, createdAt, updatedAt) VALUES ('h1', 'Apartment', 'house', 1, 1)")
            db.execSQL(
                "INSERT INTO item (id, houseId, name, quantity, unitPrice, createdAt, updatedAt) " +
                    "VALUES ('i1', 'h1', 'USB-C cable', 2, 1500, 1, 1), ('i2', 'h1', 'Raspberry Pi', 1, NULL, 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT id, name, quantity, unitPrice FROM item ORDER BY id").use { cursor ->
                cursor.moveToNext()
                assertEquals(listOf("i1", "USB-C cable", "2", "1500"), (0..3).map(cursor::getString))
                cursor.moveToNext()
                assertEquals("Raspberry Pi", cursor.getString(1))
                assertEquals(false, cursor.moveToNext())
            }
            listOf("category", "item_category", "tag", "item_tag", "token_category_count").forEach { table ->
                db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("$table starts empty", 0, cursor.getInt(0))
                }
            }
        }
    }

    @Test
    fun version2To3AddsTheTrashAndKeepsData() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL("INSERT INTO house (id, name, icon, createdAt, updatedAt) VALUES ('h1', 'Apartment', 'house', 1, 1)")
            db.execSQL("INSERT INTO item (id, houseId, name, quantity, createdAt, updatedAt) VALUES ('i1', 'h1', 'Mouse', 1, 1, 1)")
            db.execSQL("INSERT INTO tag (id, houseId, name, normalized, createdAt, updatedAt) VALUES ('t1', 'h1', 'borrowed', 'borrowed', 1, 1)")
        }
        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT (SELECT COUNT(*) FROM item) + (SELECT COUNT(*) FROM tag)").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM trash_entry").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun version3To4AddsPhotosAndKeepsData() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL("INSERT INTO house (id, name, icon, createdAt, updatedAt) VALUES ('h1', 'Apartment', 'house', 1, 1)")
            db.execSQL("INSERT INTO item (id, houseId, name, quantity, createdAt, updatedAt) VALUES ('i1', 'h1', 'Mouse', 1, 1, 1)")
            db.execSQL("INSERT INTO trash_entry (id, houseId, kind, targetId, name, parentId, trashedAt) VALUES ('t', 'h1', 'item', 'i1', 'Mouse', NULL, 5)")
        }
        helper.runMigrationsAndValidate(DB, 4, true).use { db ->
            db.query("SELECT (SELECT COUNT(*) FROM item) + (SELECT COUNT(*) FROM trash_entry)").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM photo").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun theCurrentVersionIsTheLatestExportedSchema() {
        assertEquals(4, TrecosDatabase.VERSION)
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
