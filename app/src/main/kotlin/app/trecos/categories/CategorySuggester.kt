package app.trecos.categories

import app.trecos.ui.language.AppLanguage
import org.json.JSONObject

/**
 * Suggests categories from an item's name and description (design D7): a
 * bundled English and Portuguese keyword dictionary, the categories' own
 * names, and word-category counts learned in the house. Suggestions are only
 * shown; nothing is assigned automatically.
 *
 * @param keywords keyword phrases per category key, from the bundled asset.
 * @param contexts words per top-level key that favour its subcategories ("cabo" favours Cables).
 */
class CategorySuggester(
    private val keywords: Map<String, List<String>>,
    private val contexts: Map<String, List<String>>,
) {

    /**
     * Picks up to [limit] categories for the text.
     *
     * @param text the item's name and description.
     * @param catalog the house's categories.
     * @param assigned categories already on the item, never suggested.
     * @param learned learned counts: token to (category id to count).
     * @param limit the most suggestions to return.
     * @return category ids, best first; empty when nothing matches.
     */
    fun suggest(
        text: String,
        catalog: CategoryCatalog,
        assigned: Collection<String>,
        learned: Map<String, Map<String, Int>>,
        limit: Int = 3,
    ): List<String> {
        val tokens = tokens(text)
        if (tokens.isEmpty()) return emptyList()
        val joined = " ${tokens.joinToString(" ")} "
        val scores = HashMap<String, Double>()

        /** @return the phrase's token count if it appears in the text, else 0. */
        fun matched(phrase: List<String>) = if (phrase.isNotEmpty() && " ${phrase.joinToString(" ")} " in joined) phrase.size else 0

        val activeContexts = contexts.filterValues { words -> words.any { matched(tokens(it)) > 0 } }.keys
        for (category in catalog.all) {
            if (category.parentId == null) continue
            val phrases = (keywords[category.id].orEmpty() + namePhrases(category)).map(::tokens).distinct()
            val score = phrases.sumOf { phrase -> matched(phrase).let { if (it > 0) 1 + it else 0 } }
            if (score > 0) scores[category.id] = score + if (category.parentId in activeContexts) 1.0 else 0.0
        }
        for (token in learnable(tokens)) {
            learned[token]?.forEach { (id, count) ->
                if (catalog[id] != null) scores[id] = (scores[id] ?: 0.0) + 2.0 * count.coerceAtMost(5)
            }
        }
        val order = catalog.all.withIndex().associate { (i, c) -> c.id to i }
        return scores.filterKeys { it !in assigned }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { order[it.key] ?: Int.MAX_VALUE })
            .take(limit)
            .map { it.key }
    }

    /**
     * @param category a category.
     * @return its names in both languages split into phrases ("SSDs", "Cartões SD e microSD" gives "cartoes sd", "microsd").
     */
    private fun namePhrases(category: Category): List<String> =
        listOf(category.name(AppLanguage.English), category.name(AppLanguage.PortugueseBrazil))
            .flatMap { it.split("&", "/", ",", " e ", " and ", "(", ")") }
            .map(String::trim)
            .filter { it.length >= 2 }

    companion object {
        /**
         * Records that the words of an item's text came with its categories,
         * so later suggestions in the house learn the user's vocabulary.
         *
         * @param dao the category DAO.
         * @param houseId the item's house.
         * @param text the item's name and description.
         * @param categoryIds the categories saved on the item.
         */
        suspend fun learn(dao: app.trecos.data.CategoryDao, houseId: String, text: String, categoryIds: List<String>) {
            for (token in learnable(tokens(text))) for (id in categoryIds) dao.learn(houseId, token, id)
        }

        /**
         * Reads the learned counts for the words of a text.
         *
         * @param dao the category DAO.
         * @param houseId the house.
         * @param text the item's name and description.
         * @return token to (category id to count).
         */
        suspend fun learned(dao: app.trecos.data.CategoryDao, houseId: String, text: String): Map<String, Map<String, Int>> {
            val words = learnable(tokens(text))
            if (words.isEmpty()) return emptyMap()
            return dao.learned(houseId, words).groupBy { it.token }.mapValues { (_, rows) -> rows.associate { it.categoryId to it.count } }
        }

        private val separators = Regex("[^a-z0-9+#]+")
        private val stopWords = setOf("de", "da", "do", "das", "dos", "para", "com", "e", "a", "o", "the", "and", "for", "with", "of", "to")

        /**
         * Splits text into matching tokens: normalised (case and accents),
         * split on punctuation, and a trailing plural "s" removed.
         *
         * @param text any text.
         * @return the tokens, in order.
         */
        fun tokens(text: String): List<String> =
            TextNormalizer.normalize(text).split(separators)
                .filter { it.isNotEmpty() }
                .map { if (it.length > 3 && it.endsWith("s")) it.dropLast(1) else it }

        /**
         * @param tokens tokens from [tokens].
         * @return the distinct tokens worth learning: no stop words, at least two characters.
         */
        fun learnable(tokens: List<String>): List<String> = tokens.filter { it.length >= 2 && it !in stopWords }.distinct()

        /**
         * Parses the bundled keyword asset.
         *
         * @param json the contents of `assets/category-keywords.json`.
         * @return the suggester.
         */
        fun parse(json: String): CategorySuggester {
            val root = JSONObject(json)
            fun JSONObject.lists(): Map<String, List<String>> = keys().asSequence().associateWith { key ->
                getJSONArray(key).let { array -> (0 until array.length()).map(array::getString) }
            }
            return CategorySuggester(root.getJSONObject("keywords").lists(), root.getJSONObject("contexts").lists())
        }
    }
}
