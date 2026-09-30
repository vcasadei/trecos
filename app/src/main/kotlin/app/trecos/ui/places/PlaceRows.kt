package app.trecos.ui.places

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trecos.R
import app.trecos.data.Container
import app.trecos.data.Item
import app.trecos.data.ListView
import app.trecos.places.Money
import app.trecos.places.PlaceValue
import app.trecos.ui.language.AppLanguage
import app.trecos.ui.text.SafeText
import app.trecos.ui.theme.PaletteColor

/**
 * Returns the test tag of a list row.
 *
 * @param id the record id.
 * @return the tag, such as `row_<id>`.
 */
fun rowTag(id: String): String = "row_$id"

/**
 * Returns the test tag of a row's icon badge.
 *
 * @param iconKey the icon key.
 * @return the tag, such as `badge_drawer`.
 */
fun badgeTag(iconKey: String): String = "badge_$iconKey"

/** Test tag of a row's colour stripe. */
const val STRIPE_TAG = "stripe"

/**
 * A container in a list: colour stripe, icon, name, and its item count and value.
 *
 * @param container the container.
 * @param colour its colour, own or inherited, or `null` for none.
 * @param value its folded value.
 * @param listView condensed or detailed.
 * @param currency the display currency code.
 * @param onClick opens the container.
 */
@Composable
fun ContainerRow(container: Container, colour: PaletteColor?, value: PlaceValue?, listView: ListView, currency: String, onClick: () -> Unit) {
    val language = AppLanguage.current()
    val summary = buildList {
        value?.let { add(pluralStringResource(R.plurals.item_count, it.items, it.items)) }
        value?.value?.takeIf { it > 0 }?.let { add(Money.format(it, currency, language)) }
    }.joinToString(" · ")
    PlaceRow(
        id = container.id,
        stripe = colour?.band,
        icon = PlaceIcons.container(container.icon),
        iconKey = container.icon,
        name = container.name,
        listView = listView,
        onClick = onClick,
        details = buildList {
            if (listView == ListView.Detailed) container.description?.let { add(it to 2) }
            if (summary.isNotEmpty()) add(summary to 1)
        },
    )
}

/**
 * An item in a list: icon (its photo from 0.5), name, and in the detailed
 * view its description, quantity and unit price. Empty fields are hidden.
 *
 * @param item the item.
 * @param listView condensed or detailed.
 * @param currency the display currency code.
 * @param onClick opens the item.
 */
@Composable
fun ItemRow(item: Item, listView: ListView, currency: String, onClick: () -> Unit) {
    val language = AppLanguage.current()
    val details = if (listView == ListView.Detailed) {
        buildList {
            item.description?.let { add(it to 2) }
            val numbers = listOfNotNull(
                "${stringResource(R.string.field_quantity)}: ${item.quantity}",
                item.unitPrice?.let { "${stringResource(R.string.field_unit_price)}: ${Money.format(it, currency, language)}" },
            ).joinToString(" · ")
            add(numbers to 1)
        }
    } else {
        emptyList()
    }
    PlaceRow(id = item.id, stripe = null, icon = R.drawable.ic_item, iconKey = "item", name = item.name, listView = listView, onClick = onClick, details = details)
}

/**
 * The shared row layout of both lists.
 *
 * @param id the record id, for the test tag.
 * @param stripe the colour stripe at the start, or `null` for none.
 * @param icon the icon shown where a photo would be.
 * @param iconKey the icon's key, for the badge's test tag.
 * @param name the name, on one line.
 * @param listView condensed (48 dp icon) or detailed (96 dp icon).
 * @param onClick opens the record.
 * @param details extra lines, each with its line limit.
 */
@Composable
private fun PlaceRow(
    id: String,
    stripe: Color?,
    @DrawableRes icon: Int,
    iconKey: String,
    name: String,
    listView: ListView,
    onClick: () -> Unit,
    details: List<Pair<String, Int>>,
) {
    val detailed = listView == ListView.Detailed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (detailed) 112.dp else 64.dp)
            .clickable(onClick = onClick)
            .testTag(rowTag(id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(if (detailed) 96.dp else 48.dp)
                .then(if (stripe != null) Modifier.background(stripe).testTag(STRIPE_TAG) else Modifier),
        )
        Row(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.testTag(badgeTag(iconKey))) { PlaceIconBadge(icon, if (detailed) 96.dp else 48.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SafeText(name, maxLines = 1, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                details.forEach { (text, lines) ->
                    SafeText(text, maxLines = lines, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
