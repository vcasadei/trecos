package app.trecos.categories

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trecos.data.TrecosDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scenarios "Reusing a tag" and "Same tag, different case", plus renaming and
 * deleting everywhere (task 4.6).
 */
@RunWith(RobolectricTestRunner::class)
class TagStoreTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrecosDatabase::class.java)
        .allowMainThreadQueries().build()
    private var ids = 0
    private val store = TagStore(db.tags(), clock = { 1L }, newId = { "id${ids++}" })

    @After
    fun close() = db.close()

    /** @return the tag names on an item. */
    private fun tagsOf(itemId: String) = runBlocking { db.tags().forItem(itemId).map { it.name } }

    @Test
    fun reusingATag() = runBlocking {
        store.setForItem("h1", "a", listOf("borrowed"))
        val offered = store.complete("h1", "Borr")
        assertEquals(listOf("borrowed"), offered.map { it.name })

        store.setForItem("h1", "b", listOf(offered.single().name))

        assertEquals(1, db.tags().all("h1").size)
        assertEquals(listOf("borrowed"), tagsOf("b"))
    }

    @Test
    fun sameTagDifferentCase() = runBlocking {
        store.setForItem("h1", "a", listOf("borrowed"))
        store.setForItem("h1", "b", listOf("Borrowed"))
        store.setForItem("h1", "c", listOf("BORROWÉD", "borrowed"))

        assertEquals(listOf("borrowed"), db.tags().all("h1").map { it.name })
        assertEquals(listOf("borrowed"), tagsOf("c"))
    }

    @Test
    fun tagsBelongToTheirHouse() = runBlocking {
        store.setForItem("h1", "a", listOf("borrowed"))
        assertEquals(emptyList<String>(), store.complete("h2", "bor").map { it.name })
        assertNull(store.findOrCreate("h1", "  "))
    }

    @Test
    fun renamingAppliesEverywhereAndMergesDuplicates() = runBlocking {
        store.setForItem("h1", "a", listOf("lent"))
        store.setForItem("h1", "b", listOf("lent", "borrowed"))
        val lent = db.tags().find("h1", "lent")!!

        store.rename(lent, "Emprestado")
        assertEquals(listOf("Emprestado"), tagsOf("a"))

        store.rename(db.tags().find("h1", "emprestado")!!, "BORROWED")
        assertEquals(listOf("borrowed"), tagsOf("a"))
        assertEquals(listOf("borrowed"), tagsOf("b"))
        assertEquals(1, db.tags().observeAll("h1").first().size)
    }

    @Test
    fun deletingRemovesItFromEveryItem() = runBlocking {
        store.setForItem("h1", "a", listOf("broken", "borrowed"))
        store.setForItem("h1", "b", listOf("broken"))
        store.delete(db.tags().find("h1", "broken")!!)

        assertEquals(listOf("borrowed"), tagsOf("a"))
        assertEquals(emptyList<String>(), tagsOf("b"))
    }
}
