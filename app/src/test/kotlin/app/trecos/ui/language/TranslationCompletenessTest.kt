package app.trecos.ui.language

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/**
 * Checks that every translatable English string has a Portuguese (Brazil) version.
 */
class TranslationCompletenessTest {

    /**
     * Reads the string names of a `strings.xml` file.
     *
     * @param path the file, relative to the app module.
     * @param translatableOnly whether to skip strings marked `translatable="false"`.
     * @return the string names.
     */
    private fun names(path: String, translatableOnly: Boolean): Set<String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(path)).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { !translatableOnly || it.getAttribute("translatable") != "false" }
            .map { it.getAttribute("name") }
            .toSet()
    }

    @Test
    fun portugueseHasEveryTranslatableString() {
        val english = names("src/main/res/values/strings.xml", translatableOnly = true)
        val portuguese = names("src/main/res/values-pt-rBR/strings.xml", translatableOnly = false)
        assertEquals(english, portuguese)
    }
}
