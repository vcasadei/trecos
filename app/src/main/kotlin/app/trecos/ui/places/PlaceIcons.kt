package app.trecos.ui.places

import androidx.annotation.DrawableRes
import app.trecos.R

/**
 * A bundled icon shown for a house or container that has no photo.
 *
 * @property key the stable value stored in the database; never rename it.
 * @property drawable the icon resource.
 */
data class PlaceIcon(val key: String, @param:DrawableRes val drawable: Int)

/** The icon sets for houses and containers, with their defaults first. */
object PlaceIcons {
    /** Icons for houses; the first is the default. */
    val houses = listOf(
        PlaceIcon("house", R.drawable.ic_place_house),
        PlaceIcon("apartment", R.drawable.ic_place_apartment),
        PlaceIcon("cottage", R.drawable.ic_place_cottage),
        PlaceIcon("cabin", R.drawable.ic_place_cabin),
        PlaceIcon("villa", R.drawable.ic_place_villa),
        PlaceIcon("office", R.drawable.ic_place_office),
        PlaceIcon("store", R.drawable.ic_place_store),
        PlaceIcon("warehouse", R.drawable.ic_place_warehouse),
        PlaceIcon("garage", R.drawable.ic_place_garage),
        PlaceIcon("car", R.drawable.ic_place_car),
    )

    /** Icons for containers; the first is the default. */
    val containers = listOf(
        PlaceIcon("box", R.drawable.ic_container_box),
        PlaceIcon("shelf", R.drawable.ic_container_shelf),
        PlaceIcon("drawer", R.drawable.ic_container_drawer),
        PlaceIcon("wardrobe", R.drawable.ic_container_wardrobe),
        PlaceIcon("cabinet", R.drawable.ic_container_cabinet),
        PlaceIcon("desk", R.drawable.ic_container_desk),
        PlaceIcon("tray", R.drawable.ic_container_tray),
        PlaceIcon("bag", R.drawable.ic_container_bag),
        PlaceIcon("shopping_bag", R.drawable.ic_container_shopping_bag),
        PlaceIcon("case", R.drawable.ic_container_case),
        PlaceIcon("package", R.drawable.ic_container_package),
        PlaceIcon("fridge", R.drawable.ic_container_fridge),
    )

    /** The default house icon key. */
    val defaultHouse: String get() = houses.first().key

    /** The default container icon key. */
    val defaultContainer: String get() = containers.first().key

    /**
     * @param key a stored house icon key.
     * @return its icon resource, or the default house icon for an unknown key.
     */
    @DrawableRes
    fun house(key: String): Int = houses.firstOrNull { it.key == key }?.drawable ?: houses.first().drawable

    /**
     * @param key a stored container icon key.
     * @return its icon resource, or the default container icon for an unknown key.
     */
    @DrawableRes
    fun container(key: String): Int = containers.firstOrNull { it.key == key }?.drawable ?: containers.first().drawable
}
