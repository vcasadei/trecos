package app.trecos.places

import app.trecos.categories.CategoryCatalog
import app.trecos.categories.TextNormalizer
import app.trecos.data.Container
import app.trecos.data.Item

/** What search results include. */
enum class SearchScope { Items, Containers, Both }

/** Which fields typed words are matched in. */
enum class MatchIn { NameAndDescription, Name, Description }

/** How results are ordered. */
enum class SortBy { Name, DateAdded, UnitPrice }

/**
 * Everything that narrows and orders a search, except the typed text.
 *
 * @property scope items, containers or both.
 * @property matchIn which fields typed words are matched in.
 * @property houses house ids to include; empty means all houses.
 * @property categories category ids or keys; a top level includes its subcategories; any of them matches.
 * @property tags normalised tag names; any of them matches.
 * @property sortBy the order.
 * @property descending whether the order is reversed; items without a price stay last either way.
 */
data class SearchFilters(
    val scope: SearchScope = SearchScope.Items,
    val matchIn: MatchIn = MatchIn.NameAndDescription,
    val houses: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val sortBy: SortBy = SortBy.Name,
    val descending: Boolean = false,
) {
    /** Whether any house, category or tag filter is set. */
    val hasFilters: Boolean get() = houses.isNotEmpty() || categories.isNotEmpty() || tags.isNotEmpty()
}

/**
 * One search result.
 *
 * @property item the item, or `null` for a container result.
 * @property container the container, or `null` for an item result.
 */
data class SearchResult(val item: Item? = null, val container: Container? = null) {
    /** The result's id. */
    val id: String get() = item?.id ?: container!!.id

    /** The result's house. */
    val houseId: String get() = item?.houseId ?: container!!.houseId

    /** The result's name. */
    val name: String get() = item?.name ?: container!!.name
}

/**
 * The search rules (design D6, spec "search"): matching ignores case and
 * accents, each typed word matches the start of a word, and every word must
 * match; filters match any value within one filter and all filters together.
 */
object SearchEngine {

    /**
     * Turns typed text into an FTS4 match expression.
     *
     * @param text what the user typed.
     * @param matchIn which columns to match.
     * @return the expression, such as `rasp* 4*` or `name:rasp* name:4*`, or `null` for blank text.
     */
    fun matchExpression(text: String, matchIn: MatchIn): String? {
        val words = TextNormalizer.normalize(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val prefix = when (matchIn) {
            MatchIn.NameAndDescription -> ""
            MatchIn.Name -> "name:"
            MatchIn.Description -> "description:"
        }
        return words.joinToString(" ") { "$prefix$it*" }
    }

    /**
     * Filters and orders the candidates.
     *
     * @param items every item not in the trash.
     * @param containers every container not in the trash.
     * @param matched ids that matched the typed text, or `null` when nothing was typed.
     * @param filters the filters and order.
     * @param itemCategories each item's category ids.
     * @param itemTags each item's normalised tag names.
     * @param catalog every category, to include subcategories of a chosen top level.
     * @param within container ids to stay inside ("Search in this container"), or `null` for everywhere.
     * @return the results in order.
     */
    fun results(
        items: List<Item>,
        containers: List<Container>,
        matched: Set<String>?,
        filters: SearchFilters,
        itemCategories: Map<String, List<String>>,
        itemTags: Map<String, Set<String>>,
        catalog: CategoryCatalog,
        within: Set<String>?,
    ): List<SearchResult> {
        val categories = filters.categories.flatMap { id -> listOf(id) + catalog.children(id).map { it.id } }.toSet()
        fun inHouse(houseId: String) = filters.houses.isEmpty() || houseId in filters.houses
        fun textOk(id: String) = matched == null || id in matched
        val itemResults = if (filters.scope == SearchScope.Containers) emptyList() else items.filter { item ->
            textOk(item.id) && inHouse(item.houseId) &&
                (within == null || item.containerId in within) &&
                (categories.isEmpty() || itemCategories[item.id].orEmpty().any { it in categories }) &&
                (filters.tags.isEmpty() || itemTags[item.id].orEmpty().any { it in filters.tags })
        }.map { SearchResult(item = it) }
        val containerResults = if (filters.scope == SearchScope.Items || categories.isNotEmpty() || filters.tags.isNotEmpty()) {
            emptyList()
        } else {
            containers.filter { textOk(it.id) && inHouse(it.houseId) && (within == null || it.id in within) }.map { SearchResult(container = it) }
        }
        return sort(itemResults + containerResults, filters)
    }

    /**
     * Orders results. Items without a unit price (and containers) come last
     * when sorting by price, in both directions.
     *
     * @param results the results.
     * @param filters the order.
     * @return the ordered results.
     */
    fun sort(results: List<SearchResult>, filters: SearchFilters): List<SearchResult> {
        val byName = compareBy<SearchResult> { TextNormalizer.normalize(it.name) }
        return when (filters.sortBy) {
            SortBy.Name -> results.sortedWith(if (filters.descending) byName.reversed() else byName)
            SortBy.DateAdded -> {
                val byDate = compareBy<SearchResult> { it.item?.createdAt ?: it.container!!.createdAt }
                results.sortedWith((if (filters.descending) byDate.reversed() else byDate).then(byName))
            }
            SortBy.UnitPrice -> {
                val (priced, unpriced) = results.partition { it.item?.unitPrice != null }
                val byPrice = compareBy<SearchResult> { it.item!!.unitPrice!! }
                priced.sortedWith((if (filters.descending) byPrice.reversed() else byPrice).then(byName)) + unpriced.sortedWith(byName)
            }
        }
    }
}
