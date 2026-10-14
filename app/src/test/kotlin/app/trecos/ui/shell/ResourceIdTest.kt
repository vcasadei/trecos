package app.trecos.ui.shell

import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import app.trecos.ui.places.PlacesTestBase
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The device flows find controls by their Compose test tags, which Android
 * exposes as resource ids only below a node that turns that on.
 */
@RunWith(RobolectricTestRunner::class)
class ResourceIdTest : PlacesTestBase() {

    @Test
    fun testTagsAreExposedAsResourceIds() {
        val exposed = SemanticsMatcher.expectValue(SemanticsPropertiesAndroid.TestTagsAsResourceId, true)
        rule.onNode(hasTestTag(tabTag(TrecosTab.Home)) and hasAnyAncestor(exposed), useUnmergedTree = true).assertExists()
    }
}
