package app.trecos.ui.places

/**
 * The navigation actions place screens need, so screens don't depend on the
 * navigation graph.
 *
 * @property back goes back one screen.
 * @property openHouse opens a house's top level on the Home tab.
 * @property openContainer opens a container screen.
 * @property openItem opens an item screen.
 * @property addHouse opens the new-house form.
 * @property editHouse opens a house's edit form.
 * @property addContainer opens the new-container form inside a house and parent (or the top level).
 * @property editContainer opens a container's edit form.
 * @property addItem opens the new-item form inside a house and container (or the top level).
 * @property editItem opens an item's edit form.
 * @property openTags opens a house's tags.
 */
data class PlaceNavigation(
    val back: () -> Unit,
    val openHouse: (houseId: String) -> Unit,
    val openContainer: (houseId: String, containerId: String) -> Unit,
    val openItem: (itemId: String) -> Unit,
    val addHouse: () -> Unit,
    val editHouse: (houseId: String) -> Unit,
    val addContainer: (houseId: String, parentId: String?) -> Unit,
    val editContainer: (containerId: String) -> Unit,
    val addItem: (houseId: String, containerId: String?) -> Unit,
    val editItem: (itemId: String) -> Unit,
    val openTags: (houseId: String) -> Unit,
)
