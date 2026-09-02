package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.ContactMatch
import dev.pgm.roadmate.domain.model.PhoneLabel

/**
 * After "llama a Ana" turned up several people — or one person with several
 * numbers — RoadMate asks "dime cuál". This resolves the driver's next
 * utterance against that list — an ordinal ("la segunda", "el último"), a
 * number label ("la de trabajo", "el móvil") or a distinguishing word from
 * the name ("García", "Ana López") — to a single contact, or null if it can't.
 */
object CallFollowUpParser {

    private val ORDINALS: List<Pair<Regex, Int>> = listOf(
        ordinal("primer[oa]?", 0),
        ordinal("segund[oa]", 1),
        ordinal("tercer[oa]?", 2),
        ordinal("cuart[oa]", 3),
        ordinal("quint[oa]", 4),
    )
    private val LAST = spanishRegex("""\b[uú]ltim[oa]\b""")

    private val LABEL_PHRASES: List<Pair<Regex, PhoneLabel>> = listOf(
        labelPhrase("m[oó]vil|celular", PhoneLabel.MOBILE),
        labelPhrase("trabajo|oficina|curro|curre", PhoneLabel.WORK),
        labelPhrase("casa|fij[oa]|domicilio", PhoneLabel.HOME),
        labelPhrase("principal", PhoneLabel.MAIN),
    )

    private fun ordinal(word: String, index: Int) =
        spanishRegex("""\b$word\b""") to index

    private fun labelPhrase(words: String, label: PhoneLabel) =
        spanishRegex("""\b(?:$words)\b""") to label

    fun resolve(userInput: String, candidates: List<ContactMatch>): ContactMatch? {
        if (candidates.isEmpty()) return null
        val text = userInput.trim()

        if (LAST.containsMatchIn(text)) return candidates.last()
        ORDINALS.firstOrNull { it.first.containsMatchIn(text) }
            ?.let { return candidates.getOrNull(it.second) }

        // A label the contact typed themselves ("Coche") is matched before the
        // standard ones — it's the word RoadMate just offered them.
        candidates.singleOrNull { c ->
            c.customLabel?.takeIf { it.isNotBlank() }
                ?.let { spanishRegex("""\b${Regex.escape(it)}\b""").containsMatchIn(text) } == true
        }?.let { return it }

        LABEL_PHRASES.firstOrNull { it.first.containsMatchIn(text) }?.let { (_, label) ->
            candidates.singleOrNull { it.label == label }?.let { return it }
        }

        val names = candidates.map { it.name.lowercase() }
        val words = text.lowercase()
            // Unicode-aware: an ASCII \W split turned "Ana García" into
            // "ana", "garc", "a", so a surname with an accent never matched.
            .split(spanishRegex("""\W+""", ignoreCase = false))
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
