package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.ContactMatch

/**
 * After "llama a Ana" turned up several people, RoadMate asks "dime cuál".
 * This resolves the driver's next utterance against that list — an ordinal
 * ("la segunda", "el último") or a distinguishing word from the name
 * ("García", "Ana López") — to a single contact, or null if it can't.
 */
object CallFollowUpParser {

    private val ORDINALS: List<Pair<Regex, Int>> = listOf(
        ordinal("primer[oa]?", 0),
        ordinal("segund[oa]", 1),
        ordinal("tercer[oa]?", 2),
        ordinal("cuart[oa]", 3),
        ordinal("quint[oa]", 4),
    )
    private val LAST = Regex("""\b[uú]ltim[oa]\b""", RegexOption.IGNORE_CASE)

    private fun ordinal(word: String, index: Int) =
        Regex("""\b$word\b""", RegexOption.IGNORE_CASE) to index

    fun resolve(userInput: String, candidates: List<ContactMatch>): ContactMatch? {
        if (candidates.isEmpty()) return null
        val text = userInput.trim()

        if (LAST.containsMatchIn(text)) return candidates.last()
        ORDINALS.firstOrNull { it.first.containsMatchIn(text) }
            ?.let { return candidates.getOrNull(it.second) }

        val names = candidates.map { it.name.lowercase() }
        val words = text.lowercase()
            .split(Regex("""\W+"""))
            .filter { it.length >= 3 && it !in STOPWORDS }
        // Words shared by every candidate (e.g. the first name they all match
        // on) tell nothing apart — only the distinguishing ones count.
        val distinguishing = words.filter { w -> names.count { it.contains(w) } < names.size }
        if (distinguishing.isEmpty()) return null

        val hits = candidates.filterIndexed { i, _ -> distinguishing.any { names[i].contains(it) } }
        return hits.singleOrNull()
    }

    private val STOPWORDS = setOf("con", "por", "para", "que", "del", "los", "las", "una", "uno")
}
