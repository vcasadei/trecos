package app.trecos.ui.places

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trecos.R
import app.trecos.ui.theme.LocalDarkTheme
import app.trecos.ui.theme.PaletteColor

/** Test tag of the "+" button. */
const val ADD_BUTTON_TAG = "add_button"

/** Space to leave under scrolling content so the floating bottom bar never covers it. */
val BottomBarClearance = 128.dp

/**
 * A place's icon on a tinted rounded square, shown where a photo would be.
 *
 * @param icon the icon resource.
 * @param size the square's size: 48 dp in condensed rows, 96 dp in detailed rows and headers.
 * @param tint the square's colour; the theme's surface variant by default.
 */
@Composable
fun PlaceIconBadge(@DrawableRes icon: Int, size: Dp, tint: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 6))
            .background(tint)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(size / 6)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(size / 2), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A row of palette colours to choose from, plus "No colour".
 *
 * @param selected the chosen colour key, or `null` for none.
 * @param onSelect called with the tapped colour key, or `null` for "No colour".
 */
@Composable
fun ColourPicker(selected: String?, onSelect: (String?) -> Unit) {
    val dark = LocalDarkTheme.current
    val noColour = stringResource(R.string.colour_none)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Swatch(color = MaterialTheme.colorScheme.background, selected = selected == null, label = noColour) { onSelect(null) }
        }
        items(PaletteColor.entries) { palette ->
            Swatch(color = palette.tint(dark), selected = selected == palette.key, label = palette.key) { onSelect(palette.key) }
        }
    }
}

/**
 * One round colour choice; the chosen one has a thick outline.
 *
 * @param color the swatch colour.
 * @param selected whether it is the current choice.
 * @param label spoken by screen readers and used as the test tag.
 * @param onClick called when tapped.
 */
@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = if (selected) 1f else 0.3f), CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .testTag("colour_$label"),
    )
}

/**
 * A row of icons to choose from.
 *
 * @param icons the icon set.
 * @param selected the chosen icon key.
 * @param onSelect called with the tapped icon key.
 */
@Composable
fun IconPicker(icons: List<PlaceIcon>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(icons) { icon ->
            val isSelected = icon.key == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.RadioButton) { onSelect(icon.key) }
                    .semantics {
                        contentDescription = icon.key
                        this.selected = isSelected
                    }
                    .testTag("icon_${icon.key}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon.drawable),
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * The "+" button with its two options, "Item" and "Container", which float
 * to its left when it is tapped. Tapping outside or pressing back closes them.
 *
 * @param onAddItem called when "Item" is chosen.
 * @param onAddContainer called when "Container" is chosen.
 * @param modifier modifier for the button's area.
 */
@Composable
fun AddSpeedDial(onAddItem: () -> Unit, onAddContainer: () -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }
    Box(Modifier.fillMaxSize()) {
        if (open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = false }
                    .testTag("add_scrim"),
            )
        }
        Row(
            modifier = modifier.align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (open) {
                DialOption(label = stringResource(R.string.add_item), icon = R.drawable.ic_item) {
                    open = false
                    onAddItem()
                }
                DialOption(label = stringResource(R.string.add_container), icon = R.drawable.ic_container_box) {
                    open = false
                    onAddContainer()
                }
            }
            FloatingActionButton(
                onClick = { open = !open },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag(ADD_BUTTON_TAG),
            ) {
                Icon(
                    painterResource(if (open) R.drawable.ic_close else R.drawable.ic_add),
                    contentDescription = stringResource(if (open) R.string.close else R.string.action_add),
                )
            }
        }
    }
}

/**
 * One labelled option of the speed dial.
 *
 * @param label the option's text.
 * @param icon the option's icon.
 * @param onClick called when chosen.
 */
@Composable
private fun DialOption(label: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(painterResource(icon), contentDescription = null)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp),
        )
    }
}
