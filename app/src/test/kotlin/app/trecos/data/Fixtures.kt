package app.trecos.data

/** Builders for test records with fixed timestamps. */
object Fixtures {
    /** A fixed time for created and updated stamps. */
    const val NOW = 1_700_000_000_000L

    /**
     * @param id the id.
     * @param name the name.
     * @return a house with the default icon.
     */
    fun house(id: String = "h1", name: String = "Apartment") =
        House(id = id, name = name, icon = "house", createdAt = NOW, updatedAt = NOW)

    /**
     * @param id the id.
     * @param parentId the parent container, or `null` for the top level.
     * @param name the name.
     * @param houseId the house.
     * @return a container with the default icon.
     */
    fun container(id: String, parentId: String? = null, name: String = id, houseId: String = "h1") =
        Container(id = id, houseId = houseId, parentId = parentId, name = name, icon = "box", createdAt = NOW, updatedAt = NOW)

    /**
     * @param id the id.
     * @param containerId the container, or `null` for the top level.
     * @param quantity the quantity.
     * @param unitPrice the unit price in minor units, or `null`.
     * @param houseId the house.
     * @return an item.
     */
    fun item(id: String, containerId: String? = null, quantity: Int = 1, unitPrice: Long? = null, houseId: String = "h1") =
        Item(id = id, houseId = houseId, containerId = containerId, name = id, quantity = quantity, unitPrice = unitPrice, createdAt = NOW, updatedAt = NOW)
}
