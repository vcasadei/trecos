package app.trecos.ui.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

/** The separator between breadcrumb levels. */
const val BREADCRUMB_SEPARATOR = " > "

/** The label of the collapsed levels. */
const val BREADCRUMB_COLLAPSED = "…"

/** Test tag of the collapsed "…" segment. */
const val BREADCRUMB_COLLAPSED_TAG = "breadcrumb_collapsed"

/**
 * One piece of a breadcrumb as shown.
 */
sealed interface Crumb {
    /**
     * A level of the path.
     *
     * @property index the level's position in the full path, 0 being the house.
     * @property label the level's name.
     */
    data class Level(val index: Int, val label: String) : Crumb

    /** The "…" that stands for the collapsed levels. */
    data object Collapsed : Crumb
}

/**
 * Chooses what a breadcrumb shows. When the whole path fits, every level is
 * shown; otherwise the levels before the last two collapse into one "…".
 *
 * @param levels the path from the house down, at least one level.
 * @param fitsOnOneLine whether the full path fits the available width.
 * @return the crumbs to show, in order.
 */
fun breadcrumbCrumbs(levels: List<String>, fitsOnOneLine: Boolean): List<Crumb> {
    val all = levels.mapIndexed { index, label -> Crumb.Level(index, label) }
    return if (fitsOnOneLine || levels.size <= 2) all else listOf(Crumb.Collapsed) + all.takeLast(2)
}

/**
 * A one-line, tappable location path such as "Apartment > Office > Box A".
 * When it doesn't fit, it shows "… > Box A > Cables bag"; the two remaining
 * levels still end with "…" if they are too long.
 *
 * @param levels the path from the house down.
 * @param onLevelClick called with the index of the level tapped.
 * @param onCollapsedClick called when "…" is tapped, to show the full path.
 * @param modifier modifier for the breadcrumb.
 */
@Composable
fun Breadcrumb(
    levels: List<String>,
    onLevelClick: (Int) -> Unit,
    onCollapsedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fullWidth = measurer.measure(
            text = levels.joinToString(BREADCRUMB_SEPARATOR),
            style = style,
            maxLines = 1,
            constraints = Constraints(),
        ).size.width
        val crumbs = breadcrumbCrumbs(levels, fitsOnOneLine = fullWidth <= constraints.maxWidth)
        Row(verticalAlignment = Alignment.CenterVertically) {
            crumbs.forEachIndexed { position, crumb ->
                if (position > 0) Text(BREADCRUMB_SEPARATOR, style = style, maxLines = 1)
                when (crumb) {
                    Crumb.Collapsed -> Text(
                        text = BREADCRUMB_COLLAPSED,
                        style = style,
                        maxLines = 1,
                        modifier = Modifier.testTag(BREADCRUMB_COLLAPSED_TAG).clickable(onClick = onCollapsedClick),
                    )
                    is Crumb.Level -> Text(
                        text = crumb.label,
                        style = style,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { onLevelClick(crumb.index) },
                    )
                }
            }
        }
    }
}
