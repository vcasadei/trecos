package app.trecos.places

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trecos.categories.BuiltInCategories
import app.trecos.categories.CategoryCatalog
import app.trecos.data.Fixtures.container
import app.trecos.data.Fixtures.item
import app.trecos.data.SearchIndex
import app.trecos.data.TrecosDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scenarios of the search spec at the data level (tasks 7.3-7.6).
 */
@RunWith(RobolectricTestRunner::class)
class SearchEngineTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrecosDatabase::class.java)
        .allowMainThreadQueries().addCallback(SearchIndex.onCreate).build()
    private val catalog = CategoryCatalog(BuiltInCategories.parse(File("src/main/assets/categories.json").readText()), emptyList())

    @After
    fun close() = db.close()

    /** @return the ids matching typed text. */
    private fun search(text: String, matchIn: MatchIn = MatchIn.NameAndDescription) = runBlocking {
        db.search().matches(SearchEngine.matchExpression(text, matchIn)!!).map { it.refId }.toSet()
    }

    @Test
    fun accentInsensitive() = runBlocking {
        db.items().insert(item("head").copy(name = "Cabeça de impressão"))
        assertEquals(setOf("head"), search("cabeca"))
        assertEquals(setOf("head"), search("IMPRESSAO"))
    }

    @Test
    fun partialWords() = runBlocking {
        db.items().insert(item("pi4").copy(name = "Raspberry Pi 4"))
        db.items().insert(item("pi3").copy(name = "Raspberry Pi 3"))
        assertEquals(setOf("pi4"), search("rasp 4"))
        assertEquals(setOf("pi4", "pi3"), search("rasp"))
    }

    @Test
    fun findingBySerialNumber() = runBlocking {
        db.items().insert(item("x").copy(name = "Router", serial = "SN4478X21"))
        db.items().insert(item("y").copy(name = "Other", description = "serial SN4478X21 is on the box"))
        assertEquals(setOf("x"), search("SN4478", MatchIn.Name))
        assertEquals(setOf("y"), search("SN4478", MatchIn.Description))
        assertEquals(setOf("x", "y"), search("SN4478"))
    }

    @Test
    fun brandModelAndQrMatchAsName() = runBlocking {
        db.items().insert(item("m").copy(name = "Mouse", brand = "Logitech", model = "MX Master", qrCode = "Drawer-7"))
        assertEquals(setOf("m"), search("logi", MatchIn.Name))
        assertEquals(setOf("m"), search("master", MatchIn.Name))
        assertEquals(setOf("m"), search("drawer 7", MatchIn.Name))
    }

    @Test
    fun blankTextMatchesNothingInParticular() {
        assertNull(SearchEngine.matchExpression("  - ", MatchIn.Name))
        assertEquals("rasp* 4*", SearchEngine.matchExpression("Rasp-4", MatchIn.NameAndDescription))
        assertEquals("name:cabeca*", SearchEngine.matchExpression("Cabeça", MatchIn.Name))
    }

    private val pi = item("pi").copy(name = "Raspberry Pi", unitPrice = 45_000)
    private val cable = item("cable").copy(name = "USB-C cable", unitPrice = 1_500)
    private val hdmi = item("hdmi").copy(name = "HDMI cable")
    private val drill = item("drill").copy(name = "Drill", unitPrice = 30_000)
    private val boxA = container("boxA", name = "Box A")

    /** Runs the filters over the four fixture items and one container. */
    private fun results(filters: SearchFilters, matched: Set<String>? = null, within: Set<String>? = null) = SearchEngine.results(
        items = listOf(pi, cable, hdmi, drill),
        containers = listOf(boxA),
        matched = matched,
        filters = filters,
        itemCategories = mapOf("pi" to listOf("computers.sbcs"), "cable" to listOf("cables.usb_c"), "hdmi" to listOf("cables.hdmi"), "drill" to listOf("tools.power")),
        itemTags = mapOf("pi" to setOf("borrowed"), "cable" to setOf("borrowed"), "drill" to setOf("broken")),
        catalog = catalog,
        within = within,
    ).map { it.id }

    @Test
    fun containersOnly() {
        assertEquals(listOf("boxA"), results(SearchFilters(scope = SearchScope.Containers)))
        assertEquals(listOf("boxA", "cable", "drill", "hdmi", "pi"), results(SearchFilters(scope = SearchScope.Both)).sorted())
    }

    @Test
    fun anyWithinAllAcross() {
        val filters = SearchFilters(categories = setOf("computers.sbcs", "cables.usb_c"), tags = setOf("borrowed"))
        assertEquals(listOf("pi", "cable").sorted(), results(filters).sorted())
        assertEquals(listOf("pi"), results(filters.copy(categories = setOf("computers.sbcs"))))
    }

    @Test
    fun topLevelIncludesSubcategories() {
        assertEquals(listOf("cable", "hdmi").sorted(), results(SearchFilters(categories = setOf("cables"))).sorted())
    }

    @Test
    fun mostExpensiveFirst() {
        assertEquals(listOf("pi", "drill", "cable", "hdmi"), results(SearchFilters(sortBy = SortBy.UnitPrice, descending = true)))
        assertEquals(listOf("cable", "drill", "pi", "hdmi"), results(SearchFilters(sortBy = SortBy.UnitPrice)))
    }

    @Test
    fun nameSortIsTheDefault() {
        assertEquals(listOf("drill", "hdmi", "pi", "cable"), results(SearchFilters()))
        assertEquals(listOf("cable", "pi", "hdmi", "drill"), results(SearchFilters(descending = true)))
    }

    @Test
    fun textAndWithinNarrowResults() {
        assertEquals(listOf("pi"), results(SearchFilters(), matched = setOf("pi", "boxA")))
        assertEquals(emptyList<String>(), results(SearchFilters(), within = setOf("elsewhere")))
    }
}
