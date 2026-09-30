package app.trecos.categories

import androidx.annotation.DrawableRes
import app.trecos.R

/**
 * Category icons by key. Generated with `assets/categories.json`; every icon
 * the asset names must be here, which `BuiltInCategoriesTest` checks.
 */
object CategoryIcons {
    /** The empty default icon of custom categories. */
    const val EMPTY = "label"

    private val icons: Map<String, Int> = mapOf(
        "backpack" to R.drawable.ic_cat_backpack,
        "battery_charging_full" to R.drawable.ic_cat_battery_charging_full,
        "battery_full" to R.drawable.ic_cat_battery_full,
        "bed" to R.drawable.ic_cat_bed,
        "bolt" to R.drawable.ic_cat_bolt,
        "build" to R.drawable.ic_cat_build,
        "cable" to R.drawable.ic_cat_cable,
        "camping" to R.drawable.ic_cat_camping,
        "chair" to R.drawable.ic_cat_chair,
        "checkroom" to R.drawable.ic_cat_checkroom,
        "cleaning_services" to R.drawable.ic_cat_cleaning_services,
        "computer" to R.drawable.ic_cat_computer,
        "construction" to R.drawable.ic_cat_construction,
        "description" to R.drawable.ic_cat_description,
        "desktop_windows" to R.drawable.ic_cat_desktop_windows,
        "developer_board" to R.drawable.ic_cat_developer_board,
        "devices" to R.drawable.ic_cat_devices,
        "diamond" to R.drawable.ic_cat_diamond,
        "directions_car" to R.drawable.ic_cat_directions_car,
        "dock" to R.drawable.ic_cat_dock,
        "electrical_services" to R.drawable.ic_cat_electrical_services,
        "flashlight_on" to R.drawable.ic_cat_flashlight_on,
        "handyman" to R.drawable.ic_cat_handyman,
        "hard_drive" to R.drawable.ic_cat_hard_drive,
        "headphones" to R.drawable.ic_cat_headphones,
        "health_and_safety" to R.drawable.ic_cat_health_and_safety,
        "home" to R.drawable.ic_cat_home,
        "home_iot_device" to R.drawable.ic_cat_home_iot_device,
        "hub" to R.drawable.ic_cat_hub,
        "keyboard" to R.drawable.ic_cat_keyboard,
        "kitchen" to R.drawable.ic_cat_kitchen,
        "label" to R.drawable.ic_cat_label,
        "lan" to R.drawable.ic_cat_lan,
        "laptop_mac" to R.drawable.ic_cat_laptop_mac,
        "lightbulb" to R.drawable.ic_cat_lightbulb,
        "medical_services" to R.drawable.ic_cat_medical_services,
        "medication" to R.drawable.ic_cat_medication,
        "memory" to R.drawable.ic_cat_memory,
        "menu_book" to R.drawable.ic_cat_menu_book,
        "more_horiz" to R.drawable.ic_cat_more_horiz,
        "music_note" to R.drawable.ic_cat_music_note,
        "palette" to R.drawable.ic_cat_palette,
        "park" to R.drawable.ic_cat_park,
        "photo_camera" to R.drawable.ic_cat_photo_camera,
        "power" to R.drawable.ic_cat_power,
        "power_input" to R.drawable.ic_cat_power_input,
        "router" to R.drawable.ic_cat_router,
        "save" to R.drawable.ic_cat_save,
        "sd_card" to R.drawable.ic_cat_sd_card,
        "sensors" to R.drawable.ic_cat_sensors,
        "settings_ethernet" to R.drawable.ic_cat_settings_ethernet,
        "settings_input_hdmi" to R.drawable.ic_cat_settings_input_hdmi,
        "smartphone" to R.drawable.ic_cat_smartphone,
        "sports_soccer" to R.drawable.ic_cat_sports_soccer,
        "straighten" to R.drawable.ic_cat_straighten,
        "styler" to R.drawable.ic_cat_styler,
        "toys" to R.drawable.ic_cat_toys,
        "tv" to R.drawable.ic_cat_tv,
        "usb" to R.drawable.ic_cat_usb,
        "videocam" to R.drawable.ic_cat_videocam,
        "videogame_asset" to R.drawable.ic_cat_videogame_asset,
        "view_in_ar" to R.drawable.ic_cat_view_in_ar,
        "watch" to R.drawable.ic_cat_watch,
        "weekend" to R.drawable.ic_cat_weekend,
        "wifi" to R.drawable.ic_cat_wifi,
        "yard" to R.drawable.ic_cat_yard,
    )

    /** Every icon key, for the custom category icon picker. */
    val keys: List<String> get() = icons.keys.toList()

    /**
     * @param key an icon key, or `null`.
     * @return its drawable, or the empty default icon for `null` or an unknown key.
     */
    @DrawableRes
    fun drawable(key: String?): Int = icons[key] ?: icons.getValue(EMPTY)

    /**
     * @param key an icon key.
     * @return whether the key has an icon.
     */
    fun has(key: String): Boolean = key in icons
}
