package app.trecos.categories

import app.trecos.data.CustomCategory
import app.trecos.ui.language.AppLanguage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The built-in category tree (task 4.2): the full list from the spec, icons,
 * translations, and built-ins that can't be edited.
 */
@RunWith(RobolectricTestRunner::class)
class BuiltInCategoriesTest {

    private val builtIns = BuiltInCategories.parse(File("src/main/assets/categories.json").readText())
    private val catalog = CategoryCatalog(builtIns, emptyList())

    private val connectors = listOf(
        "USB-A", "USB-B", "USB-C", "Micro-USB", "Mini-USB", "Lightning", "HDMI", "Mini/Micro HDMI", "DisplayPort",
        "Mini DisplayPort", "VGA", "DVI", "Ethernet", "Serial/UART", "Audio", "SD/microSD",
    )

    /** The categories the spec requires, in English. */
    private val spec = linkedMapOf(
        "Computers" to listOf("Desktops", "Laptops", "SBCs & dev boards", "Components", "Peripherals"),
        "Storage" to listOf("Pendrives", "SD & microSD cards", "SSDs", "HDDs", "Enclosures & docks"),
        "Cables" to connectors + "Power cords",
        "Adapters" to connectors + "USB hubs & docks",
        "Power" to listOf("Chargers", "Power supplies", "Plug adapters", "Power strips & extensions", "Batteries", "Power banks"),
        "Electrical" to listOf(
            "Wire nuts", "Wago & lever connectors", "Heat shrink", "Terminals & crimps", "Plugs (male/female)",
            "Outlets & sockets", "Switches", "Wire & cable", "Electrical tape", "Breakers & fuses",
        ),
        "Electronics parts" to listOf("Components", "Modules & sensors", "Prototyping", "Soldering supplies"),
        "Networking" to listOf("Routers", "Switches", "Access points & mesh", "Modems"),
        "Smart home" to listOf("Smart plugs", "Smart bulbs", "Sensors", "Hubs & bridges", "Cameras"),
        "Lighting" to listOf("Lamps", "Light bulbs", "Flashlights", "LED strips"),
        "3D printing" to listOf("Filaments", "Nozzles", "Replacement parts", "Tools", "Cleaning & maintenance", "Build plates"),
        "Devices" to listOf("Phones & tablets", "Audio & video", "Games & consoles", "Cameras", "Wearables"),
        "Tools" to listOf("Hand tools", "Power tools", "Measuring", "Hardware", "Safety & PPE"),
        "Home" to listOf("Kitchen", "Appliances", "Furniture", "Decor", "Bedding & towels", "Cleaning", "Household supplies"),
        "Personal" to listOf("Clothing", "Shoes", "Bags", "Jewelry & watches"),
        "Documents" to listOf("Manuals", "Warranties", "Personal documents"),
        "Hobbies" to listOf("Books", "Music instruments", "Sports", "Crafts", "Collectibles", "Toys"),
        "Outdoor" to listOf("Garden", "Camping", "Car & bike"),
        "Health" to listOf("Medicine", "First aid"),
        "Other" to emptyList(),
    )

    @Test
    fun everyListedCategoryExists() {
        val tree = catalog.topLevel.associate { top -> top.english to catalog.children(top.id).map { it.english } }
        assertEquals(spec.keys.toList(), tree.keys.toList())
        spec.forEach { (top, subs) -> assertEquals("children of $top", subs, tree[top]) }
    }

    @Test
    fun keysAreUniqueAndStable() {
        assertEquals(builtIns.size, builtIns.map { it.id }.toSet().size)
        assertEquals("cables.usb_c", catalog.search("usb-c", AppLanguage.English).first { it.parentId == "cables" }.id)
    }

    @Test
    fun everyCategoryHasAKnownIconAndBothNames() {
        builtIns.forEach {
            assertTrue("${it.id} icon ${it.icon}", CategoryIcons.has(it.icon!!))
            assertTrue(it.english.isNotBlank() && it.portuguese.isNotBlank())
        }
    }

    @Test
    fun builtInsInPortuguese() {
        assertEquals("Cabos > USB-C", catalog.label("cables.usb_c", AppLanguage.PortugueseBrazil))
        assertEquals("Cables > USB-C", catalog.label("cables.usb_c", AppLanguage.English))
        assertEquals("Ferramentas > Segurança e EPI", catalog.label("tools.safety", AppLanguage.PortugueseBrazil))
    }

    @Test
    fun builtInsCannotBeEdited() {
        assertTrue(builtIns.none { it.editable })
        val custom = CategoryCatalog(builtIns, listOf(CustomCategory("k", "h1", "computers", "Keyboards (mechanical)", null, 1, 1)))
        assertTrue(custom["k"]!!.editable)
        assertEquals("Computers > Keyboards (mechanical)", custom.label("k", AppLanguage.English))
        assertEquals("Computadores > Keyboards (mechanical)", custom.label("k", AppLanguage.PortugueseBrazil))
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        val found = catalog.search("seguranca", AppLanguage.PortugueseBrazil).map { it.id }
        assertTrue("tools.safety" in found)
        assertFalse(catalog.search("zzz", AppLanguage.English).isNotEmpty())
        assertEquals(catalog.all.size, catalog.search("  ", AppLanguage.English).size)
    }
}
