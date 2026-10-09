package app.trecos.categories

import java.text.Normalizer

/** Text matching that ignores letter case and accents. */
object TextNormalizer {
    private val marks = Regex("\\p{Mn}+")

    /**
     * @param text any text.
     * @return the text in lower case, without accents, trimmed: "Cabo Três" becomes "cabo tres".
     */
    fun normalize(text: String): String =
        marks.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFD), "").lowercase()
}
