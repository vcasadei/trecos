package app.trecos.help

/**
 * One FAQ entry.
 *
 * @property number its number, 1 to 10.
 * @property question the question.
 * @property answer the answer, with `**bold**` kept, links reduced to their text, paragraphs separated by blank lines.
 */
data class FaqEntry(val number: Int, val question: String, val answer: String)

/** Reads the bundled FAQ (`docs/user/<language>/faq.md`, design D18). */
object Faq {
    private val heading = Regex("^## (\\d+)\\. (.+)$")
    private val link = Regex("\\[([^]]+)]\\([^)]+\\)")

    /**
     * @param markdown the FAQ file.
     * @return its entries, in order.
     */
    fun parse(markdown: String): List<FaqEntry> {
        val entries = ArrayList<FaqEntry>()
        var number = 0
        var question: String? = null
        val body = ArrayList<String>()
        fun flush() {
            val q = question ?: return
            entries += FaqEntry(number, q, answer(body))
            body.clear()
        }
        for (line in markdown.lines()) {
            val match = heading.find(line.trim())
            if (match != null) {
                flush()
                number = match.groupValues[1].toInt()
                question = match.groupValues[2].trim()
            } else if (question != null) {
                body += line
            }
        }
        flush()
        return entries
    }

    /** Joins wrapped lines into paragraphs and list items, and reduces links to their text. */
    private fun answer(lines: List<String>): String {
        val blocks = ArrayList<String>()
        val current = StringBuilder()
        fun end() {
            if (current.isNotBlank()) blocks += current.toString().trim()
            current.clear()
        }
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> end()
                line.startsWith("- ") -> {
                    end()
                    current.append("• ").append(line.removePrefix("- "))
                }
                else -> current.append(if (current.isEmpty()) "" else " ").append(line)
            }
        }
        end()
        return blocks.joinToString("\n\n") { it.replace(link, "$1") }
    }
}
