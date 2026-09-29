package app.trecos.release

import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/**
 * Checks the merged manifest points Android backup at the rule files that
 * exclude the database and key files.
 */
class MergedManifestTest {

    /**
     * Reads an attribute of the `application` element of the merged manifest,
     * whose path AGP passes to unit tests in `test_config.properties`.
     *
     * @param name the attribute name without the `android:` prefix.
     * @return the attribute value, such as `@xml/backup_rules`.
     */
    private fun applicationAttribute(name: String): String {
        val config = Properties().apply {
            MergedManifestTest::class.java.getResourceAsStream("/com/android/tools/test_config.properties").use(::load)
        }
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(config.getProperty("android_merged_manifest")))
        val application = manifest.getElementsByTagName("application").item(0) as Element
        return application.getAttributeNS("http://schemas.android.com/apk/res/android", name)
    }

    @Test
    fun mergedManifestPointsAtBothRuleFiles() {
        assertEquals("@xml/data_extraction_rules", applicationAttribute("dataExtractionRules"))
        assertEquals("@xml/backup_rules", applicationAttribute("fullBackupContent"))
    }
}
