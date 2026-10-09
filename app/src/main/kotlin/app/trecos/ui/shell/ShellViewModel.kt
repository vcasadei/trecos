package app.trecos.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trecos.AppContainer
import app.trecos.data.HouseBand
import app.trecos.ui.theme.PaletteColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Chooses the house band drawn behind the status bar.
 *
 * @param app the app's container.
 */
class ShellViewModel(app: AppContainer) : ViewModel() {

    /**
     * The current house's colour when the band shows, or `null` when it is
     * hidden. Automatic shows it with two or more houses; a house without a
     * colour uses stone.
     */
    val band: StateFlow<PaletteColor?> = combine(
        app.database.houses().observeAll(),
        app.preferences.lastHouseId,
        app.preferences.houseBand,
    ) { houses, lastId, setting ->
        val house = houses.firstOrNull { it.id == lastId } ?: houses.firstOrNull()
        val show = when (setting) {
            HouseBand.Always -> house != null
            HouseBand.Never -> false
            HouseBand.Automatic -> houses.size >= 2
        }
        if (show) PaletteColor.fromKey(house?.colorKey) ?: PaletteColor.Stone else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
