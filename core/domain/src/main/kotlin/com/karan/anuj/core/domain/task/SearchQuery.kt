package com.karan.anuj.core.domain.task

/**
 * What the user typed into search, reduced to plain words.
 *
 * Only letters and digits are kept, so nothing typed can be read as search
 * syntax by the database. Every word must be present, and each matches from
 * its start: "bu mil" finds "Buy milk".
 */
class SearchQuery private constructor(val words: List<String>) {

    /** The form the full-text index is queried with: each word as a prefix, all required. */
    val matchExpression: String get() = words.joinToString(" ") { "$it*" }

    companion object {
        /** @return null when there is nothing to search for */
        fun from(text: String): SearchQuery? {
            val words = text
                .lowercase()
                .split(Regex("[^\\p{L}\\p{Nd}]+"))
                .filter { it.isNotEmpty() }
            return if (words.isEmpty()) null else SearchQuery(words)
        }
    }
}
