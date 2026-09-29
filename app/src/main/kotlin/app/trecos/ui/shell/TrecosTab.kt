package app.trecos.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.trecos.R

/**
 * The three tabs of the bottom bar, in display order.
 *
 * @property label the tab's text label.
 * @property icon the icon shown in the raised circle when the tab is selected.
 */
enum class TrecosTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Search(R.string.tab_search, R.drawable.ic_tab_search),
    Home(R.string.tab_home, R.drawable.ic_tab_home),
    Settings(R.string.tab_settings, R.drawable.ic_tab_settings),
}
