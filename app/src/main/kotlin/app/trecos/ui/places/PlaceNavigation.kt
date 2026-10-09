package app.trecos.ui.places

/**
 * The navigation actions place screens need, so screens don't depend on the
 * navigation graph.
 *
 * @property back goes back one screen.
 * @property openHouse opens a house's top level on the Home tab: the root itself with one house, above the house list with more.
 * @property openHouseScreen opens a house's top level as its own screen, above the house list.
 * @property openContainer opens a container screen.
 * @property openItem opens an item screen.
 * @property addHouse opens the new-house form.
 * @property editHouse opens a house's edit form.
 * @property addContainer opens the new-container form inside a house and parent (or the top level).
 * @property editContainer opens a container's edit form.
 * @property addItem opens the new-item form inside a house and container (or the top level).
 * @property editItem opens an item's edit form.
 * @property openTags opens a house's tags.
 * @property openTrash opens a house's trash.
 * @property keep opens the "choose what to keep" screen for a container being deleted.
 * @property deleteHouse opens the house deletion screen.
 * @property addContainerWithCode opens the new-container form with a scanned code as its code and name.
 * @property addItemWithCode opens the new-item form with a scanned code as its code and name.
 * @property searchIn opens the Search tab (already limited to a container through `AppContainer.searchWithin`).
 */
data class PlaceNavigation(
    val back: () -> Unit,
    val openHouse: (houseId: String) -> Unit,
    val openHouseScreen: (houseId: String) -> Unit = {},
    val openContainer: (houseId: String, containerId: String) -> Unit,
    val openItem: (itemId: String) -> Unit,
    val addHouse: () -> Unit,
    val editHouse: (houseId: String) -> Unit,
    val addContainer: (houseId: String, parentId: String?) -> Unit,
    val editContainer: (containerId: String) -> Unit,
    val addItem: (houseId: String, containerId: String?) -> Unit,
    val editItem: (itemId: String) -> Unit,
    val openTags: (houseId: String) -> Unit,
    val openTrash: (houseId: String) -> Unit,
    val keep: (containerId: String) -> Unit,
    val deleteHouse: (houseId: String) -> Unit,
    val searchIn: () -> Unit,
    val addContainerWithCode: (houseId: String, parentId: String?, code: String) -> Unit = { _, _, _ -> },
    val addItemWithCode: (houseId: String, containerId: String?, code: String) -> Unit = { _, _, _ -> },
)
