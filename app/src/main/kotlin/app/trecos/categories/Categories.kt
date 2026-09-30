package app.trecos.categories

import app.trecos.data.CustomCategory
import app.trecos.ui.language.AppLanguage
import org.json.JSONObject

/**
 * One category as shown: a built-in from the bundled tree or a custom one of
 * the current house.
 *
 * @property id the built-in key (such as `cables.usb_c`) or the custom category id.
 * @property parentId the top-level category's id, or `null` for a top level.
 * @property icon the icon key, or `null` for the empty default icon.
 * @property builtIn whether it is built in, and therefore can't be renamed or deleted.
 * @property english the English name (built-ins), or the name as typed (custom).
 * @property portuguese the Portuguese name (built-ins), or the name as typed (custom).
 */
data class Category(
    val id: String,
    val parentId: String?,
    val icon: String?,
    val builtIn: Boolean,
    val english: String,
    val portuguese: String,
) {
    /** Whether the user may rename or delete it. */
    val editable: Boolean get() = !builtIn

    /**
     * @param language the app language.
     * @return the name in that language; custom names are shown exactly as typed.
     */
    fun name(language: AppLanguage): String = if (language == AppLanguage.PortugueseBrazil) portuguese else english
}

/** The built-in category tree, parsed from `assets/categories.json`. */
object BuiltInCategories {

    /**
     * Parses the bundled asset.
     *
     * @param json the asset's contents.
     * @return every built-in category, top levels followed by their children, in asset order.
     */
    fun parse(json: String): List<Category> {
        val tops = JSONObject(json).getJSONArray("categories")
        return buildList {
            for (i in 0 until tops.length()) {
                val top = tops.getJSONObject(i)
                add(top.toCategory(parentId = null))
                val children = top.getJSONArray("children")
                for (j in 0 until children.length()) add(children.getJSONObject(j).toCategory(parentId = top.getString("key")))
            }
        }
    }

    /**
     * @param parentId the parent key, or `null` for a top level.
     * @return the category this JSON object describes.
     */
    private fun JSONObject.toCategory(parentId: String?) = Category(
        id = getString("key"),
        parentId = parentId,
        icon = getString("icon"),
        builtIn = true,
        english = getString("en"),
        portuguese = getString("pt"),
    )
}

/**
 * The categories available in one house: the built-in tree plus the house's
 * custom categories.
 *
 * @param builtIns the built-in categories.
 * @param custom the house's custom categories.
 */
class CategoryCatalog(builtIns: List<Category>, custom: List<CustomCategory>) {

    /** Every category: built-ins in asset order, then custom ones by name. */
    val all: List<Category> = builtIns + custom.sortedBy { TextNormalizer.normalize(it.name) }.map {
        Category(id = it.id, parentId = it.parentId, icon = it.icon, builtIn = false, english = it.name, portuguese = it.name)
    }

    private val byId = all.associateBy { it.id }

    /**
     * @param id a category id or key.
     * @return the category, or `null` if it doesn't exist in this house.
     */
    operator fun get(id: String): Category? = byId[id]

    /** The top-level categories. */
    val topLevel: List<Category> get() = all.filter { it.parentId == null }

    /**
     * @param parentId a top-level category id.
     * @return its subcategories, built-ins first.
     */
    fun children(parentId: String): List<Category> = all.filter { it.parentId == parentId }

    /**
     * @param id a category id or key.
     * @param language the app language.
     * @return "Top > Sub" for a subcategory, the name for a top level, or `null` if unknown.
     */
    fun label(id: String, language: AppLanguage): String? {
        val category = byId[id] ?: return null
        val parent = category.parentId?.let(byId::get)
        return listOfNotNull(parent?.name(language), category.name(language)).joinToString(" > ")
    }

    /**
     * Finds categories whose name, or whose parent's name, contains the query,
     * ignoring case and accents.
     *
     * @param query what the user typed; blank returns every category.
     * @param language the app language.
     * @return the matches, in catalogue order.
     */
    fun search(query: String, language: AppLanguage): List<Category> {
        val needle = TextNormalizer.normalize(query)
        if (needle.isEmpty()) return all
        return all.filter { TextNormalizer.normalize(label(it.id, language).orEmpty()).contains(needle) }
    }
}
