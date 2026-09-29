package app.trecos.ui.text

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * Text that can't break a layout: it is limited to [maxLines] and ends with
 * "…" when it doesn't fit. A word longer than the available width wraps
 * inside the word instead of pushing other content away. Use it for all text
 * in lists, bars, chips and headers.
 *
 * @param text the text to show.
 * @param maxLines the most lines the text may take; required so every caller decides.
 * @param modifier modifier for the text; give it a weight in rows so neighbours keep their space.
 * @param style the text style.
 * @param color the text colour, or [Color.Unspecified] for the style's colour.
 */
@Composable
fun SafeText(
    text: String,
    maxLines: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
    )
}
