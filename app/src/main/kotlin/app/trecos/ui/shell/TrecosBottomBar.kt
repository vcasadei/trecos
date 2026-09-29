package app.trecos.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.trecos.ui.theme.LocalMotion

private val BarHeight = 64.dp
private val Rise = 28.dp
private val BumpRadius = 34.dp
private val DiscRadius = 28.dp
private val BumpCenterBelowTop = 6.dp

/** Test tag of the raised circle that marks the selected tab. */
const val TAB_INDICATOR_TAG = "tab_indicator"

/**
 * Returns the test tag of a tab in the bottom bar.
 *
 * @param tab the tab.
 * @return the tag, such as `tab_Search`.
 */
fun tabTag(tab: TrecosTab): String = "tab_${tab.name}"

/**
 * The floating bottom bar: Search, Home and Settings. Unselected tabs show
 * only their label; the selected one also has a raised circle with its icon,
 * which slides to a newly selected tab.
 *
 * @param selected the tab being shown.
 * @param onSelect called with the tab the user tapped.
 * @param modifier modifier for the bar's outer box.
 */
@Composable
fun TrecosBottomBar(selected: TrecosTab, onSelect: (TrecosTab) -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val colors = MaterialTheme.colorScheme
    val position by animateFloatAsState(
        targetValue = selected.ordinal.toFloat(),
        animationSpec = motion.spec(motion.mediumMs),
        label = "tabIndicator",
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(Rise + BarHeight),
    ) {
        val tabWidth = maxWidth / TrecosTab.entries.size
        val centerX = tabWidth * (position + 0.5f)
        val shape = BumpBarShape(centerX)
        Box(
            Modifier
                .fillMaxWidth()
                .height(Rise + BarHeight)
                .shadow(8.dp, shape)
                .background(colors.surface, shape),
        )
        Box(
            modifier = Modifier
                .offset(x = centerX - DiscRadius, y = Rise + BumpCenterBelowTop - DiscRadius)
                .size(DiscRadius * 2)
                .background(colors.primary, CircleShape)
                .testTag(TAB_INDICATOR_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(selected.icon), contentDescription = null, tint = colors.onPrimary)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Rise)
                .height(BarHeight)
                .selectableGroup(),
        ) {
            TrecosTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                        .testTag(tabTag(tab)),
                    contentAlignment = if (isSelected) Alignment.BottomCenter else Alignment.Center,
                ) {
                    Text(
                        text = stringResource(tab.label),
                        color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = if (isSelected) 8.dp else 0.dp),
                    )
                }
            }
        }
    }
}

/**
 * The bar's outline: a pill-shaped rounded rectangle below [Rise], joined
 * with a circle that bulges above its top edge at [centerX].
 *
 * @property centerX the horizontal centre of the bump, from the bar's start edge.
 */
private class BumpBarShape(private val centerX: Dp) : Shape {

    /**
     * Builds the combined path of the bar and its bump.
     *
     * @param size the size of the bar's box, including the space above the bar.
     * @param layoutDirection the layout direction (the bar is laid out left to right).
     * @param density converts the dp constants to pixels.
     * @return a generic outline of the combined path.
     */
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        with(density) {
            val top = Rise.toPx()
            val corner = BarHeight.toPx() / 2
            val bar = Path().apply {
                addRoundRect(RoundRect(Rect(0f, top, size.width, size.height), CornerRadius(corner)))
            }
            val bump = Path().apply {
                val center = Offset(centerX.toPx(), top + BumpCenterBelowTop.toPx())
                addOval(Rect(center, BumpRadius.toPx()))
            }
            Outline.Generic(Path.combine(PathOperation.Union, bar, bump))
        }
}
