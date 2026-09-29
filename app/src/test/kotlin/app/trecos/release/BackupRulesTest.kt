package app.trecos.release

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.trecos.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/**
 * Checks that the backup rule files exclude the database and key files.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * Collects the `exclude` rules of an XML resource.
     *
     * @param id the XML resource.
     * @return each exclusion as `domain:path`, in file order.
     */
    private fun excludes(id: Int): List<String> {
        val parser = context.resources.getXml(id)
        val found = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                found += "${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}"
            }
        }
        return found
    }

    @Test
    fun android12AndLaterExcludeTheDatabaseAndKeys() {
        val rules = excludes(R.xml.data_extraction_rules)
        assertEquals(listOf("database:.", "file:keys/", "database:.", "file:keys/"), rules)
    }

    @Test
    fun android9To11ExcludeTheDatabaseAndKeys() {
        assertTrue(excludes(R.xml.backup_rules).containsAll(listOf("database:.", "file:keys/")))
    }
}
