package app.trecos.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DAO tests for custom categories, assignments, tags and learned counts.
 */
@RunWith(RobolectricTestRunner::class)
class CategoryDaoTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrecosDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun close() = db.close()

    /** @return an assignment of [categoryId] to [itemId] at [position]. */
    private fun assign(itemId: String, categoryId: String, position: Int) =
        ItemCategory(id = "$itemId-$categoryId", houseId = "h1", itemId = itemId, categoryId = categoryId, position = position, createdAt = 1)

    @Test
    fun itemCategoriesKeepTheirOrder() = runBlocking {
        db.categories().replaceForItem("cable", listOf(assign("cable", "cables.usb_c", 0), assign("cable", "cables.usb_a", 1)))
        assertEquals(listOf("cables.usb_c", "cables.usb_a"), db.categories().forItem("cable"))

        db.categories().replaceForItem("cable", listOf(assign("cable", "cables.usb_a", 0), assign("cable", "cables.usb_c", 1)))
        assertEquals(listOf("cables.usb_a", "cables.usb_c"), db.categories().forItem("cable"))
    }

    @Test
    fun deletingACustomCategoryRemovesItFromItemsAndKeepsTheirOthers() = runBlocking {
        db.categories().insertCustom(CustomCategory(id = "mech", houseId = "h1", parentId = "computers", name = "Keyboards (mechanical)", createdAt = 1, updatedAt = 1))
        db.categories().insertCustom(CustomCategory(id = "sub", houseId = "h1", parentId = "mech", name = "Switches", createdAt = 1, updatedAt = 1))
        (1..5).forEach { db.categories().replaceForItem("k$it", listOf(assign("k$it", "mech", 0), assign("k$it", "computers.peripherals", 1))) }
        db.categories().replaceForItem("s", listOf(assign("s", "sub", 0)))
        assertEquals(5, db.categories().usage("mech"))

        db.categories().deleteCustom("mech")

        assertEquals(0, db.categories().usage("mech"))
        assertEquals(0, db.categories().usage("sub"))
        (1..5).forEach { assertEquals(listOf("computers.peripherals"), db.categories().forItem("k$it")) }
        assertEquals(emptyList<CustomCategory>(), db.categories().custom("h1"))
    }

    @Test
    fun learnedCountsAddUp() = runBlocking {
        repeat(3) { db.categories().learn("h1", "rpi", "computers.sbcs") }
        db.categories().learn("h1", "rpi", "computers.components")
        val counts = db.categories().learned("h1", listOf("rpi")).associate { it.categoryId to it.count }
        assertEquals(mapOf("computers.sbcs" to 3, "computers.components" to 1), counts)
        assertEquals(emptyList<TokenCategoryCount>(), db.categories().learned("h2", listOf("rpi")))
    }

    @Test
    fun tagsDeleteAndReassignEverywhere() = runBlocking {
        db.tags().insert(Tag(id = "t1", houseId = "h1", name = "borrowed", normalized = "borrowed", createdAt = 1, updatedAt = 1))
        db.tags().insert(Tag(id = "t2", houseId = "h1", name = "Lent", normalized = "lent", createdAt = 1, updatedAt = 1))
        db.tags().replaceForItem("a", listOf(ItemTag("a1", "h1", "a", "t1", 1), ItemTag("a2", "h1", "a", "t2", 1)))
        db.tags().replaceForItem("b", listOf(ItemTag("b2", "h1", "b", "t2", 1)))

        db.tags().reassign(from = "t2", to = "t1")
        db.tags().delete("t2")

        assertEquals(listOf("borrowed"), db.tags().forItem("a").map { it.name })
        assertEquals(listOf("borrowed"), db.tags().forItem("b").map { it.name })
        assertEquals(listOf("t1"), db.tags().observeAll("h1").first().map { it.id })
    }
}
