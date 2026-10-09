package app.trecos.categories

import app.trecos.data.CustomCategory
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scenarios "Keyword match", "Learning my vocabulary" and "Nothing recognised" (task 4.5).
 */
@RunWith(RobolectricTestRunner::class)
class CategorySuggesterTest {

    private val builtIns = BuiltInCategories.parse(File("src/main/assets/categories.json").readText())
    private val catalog = CategoryCatalog(builtIns, emptyList())
    private val suggester = CategorySuggester.parse(File("src/main/assets/category-keywords.json").readText())

    @Test
    fun keywordMatch() {
        val chips = suggester.suggest("cabo usb-c para hdmi", catalog, assigned = emptyList(), learned = emptyMap())
        assertEquals(listOf("cables.usb_c", "cables.hdmi"), chips.take(2))
        assertTrue(chips.size <= 3)
    }

    @Test
    fun alreadyAssignedCategoriesAreNotSuggested() {
        val chips = suggester.suggest("cabo usb-c para hdmi", catalog, assigned = listOf("cables.usb_c"), learned = emptyMap())
        assertEquals("cables.hdmi", chips.first())
        assertTrue("cables.usb_c" !in chips)
    }

    @Test
    fun learningMyVocabulary() {
        val learned = mapOf("rpi" to mapOf("computers.sbcs" to 4))
        val chips = suggester.suggest("rpi zero", catalog, assigned = emptyList(), learned = learned)
        assertEquals("computers.sbcs", chips.first())
    }

    @Test
    fun learningWorksForCustomCategories() {
        val house = CategoryCatalog(builtIns, listOf(CustomCategory("mech", "h1", "computers", "Keyboards (mechanical)", null, 1, 1)))
        val chips = suggester.suggest("gateron switches", house, assigned = emptyList(), learned = mapOf("gateron" to mapOf("mech" to 2)))
        assertEquals("mech", chips.first())
    }

    @Test
    fun savedItemsTeachTheSuggester() = kotlinx.coroutines.runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(), app.trecos.data.TrecosDatabase::class.java,
        ).allowMainThreadQueries().build()
        listOf("rpi 4 8gb", "rpi zero w", "rpi pico").forEach { name ->
            CategorySuggester.learn(db.categories(), "h1", name, listOf("computers.sbcs"))
        }
        val learned = CategorySuggester.learned(db.categories(), "h1", "rpi zero 2")
        assertEquals(3, learned["rpi"]?.get("computers.sbcs"))
        assertEquals("computers.sbcs", suggester.suggest("rpi zero 2", catalog, emptyList(), learned).first())
        assertEquals(emptyMap<String, Map<String, Int>>(), CategorySuggester.learned(db.categories(), "h2", "rpi"))
        db.close()
    }

    @Test
    fun nothingRecognised() {
        assertEquals(emptyList<String>(), suggester.suggest("misc thing", catalog, assigned = emptyList(), learned = emptyMap()))
        assertEquals(emptyList<String>(), suggester.suggest("", catalog, assigned = emptyList(), learned = emptyMap()))
    }

    @Test
    fun portugueseAndAccentsAreRecognised() {
        assertEquals("power.power_banks", suggester.suggest("Bateria externa Xiaomi", catalog, emptyList(), emptyMap()).first())
        assertEquals("tools.safety", suggester.suggest("Luvas de látex", catalog, emptyList(), emptyMap()).first())
        assertEquals("computers.laptops", suggester.suggest("Notebook Dell", catalog, emptyList(), emptyMap()).first())
    }

    @Test
    fun adapterContextWinsOverCables() {
        assertEquals("adapters.hdmi", suggester.suggest("adaptador hdmi", catalog, emptyList(), emptyMap()).first())
    }

    @Test
    fun everyKeywordNamesAKnownCategory() {
        val raw = org.json.JSONObject(File("src/main/assets/category-keywords.json").readText())
        val keys = raw.getJSONObject("keywords").keys().asSequence().toList()
        keys.forEach { assertTrue("unknown category $it", catalog[it] != null) }
        raw.getJSONObject("contexts").keys().forEach { assertTrue("unknown top $it", catalog[it]?.parentId == null) }
    }

    @Test
    fun tokensAreNormalised() {
        assertEquals(listOf("cabo", "usb", "c", "para", "hdmi"), CategorySuggester.tokens("Cabo USB-C para HDMI"))
        assertEquals(listOf("luva", "latex"), CategorySuggester.learnable(CategorySuggester.tokens("Luvas de Látex")))
    }
}
