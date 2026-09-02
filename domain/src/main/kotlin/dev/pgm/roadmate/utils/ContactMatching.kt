package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.ContactMatch
import dev.pgm.roadmate.domain.model.PhoneLabel
import java.text.Normalizer

/**
 * Decides who "llama a X" meant, given whatever the contacts provider handed
 * back for X.
 *
 * This lives in `:domain`, away from `ContentResolver`, because it is the one
 * piece of RoadMate that can do something irreversible to someone else:
 * [dev.pgm.roadmate.domain.repository.PhoneCallRepository.placeCall] dials
 * immediately, with no confirmation, by design — the driver's hands are on the
 * wheel. So the rule for *when* RoadMate is allowed to decide by itself has to
 * be readable and testable on its own, not buried in a cursor loop.
 *
 * The rule: only the strongest tier of match that exists is considered, and
 * RoadMate dials on its own only when that tier names one person. Anything
 * else is a question.
 */
object ContactMatching {

    /**
     * How well a contact's name answers to what was said. Lower is better;
     * only the lowest tier present is ever used.
     */
    enum class Tier {
        /** The whole name is what was said: "Ana García" for "Ana García". */
        EXACT,

        /** A word of the name starts with it: "García" for "Ana García Ruiz". */
        WORD_PREFIX,

        /**
         * It only appears somewhere inside a word — "ana" inside "Juana".
         * Real, but the kind of match that must never dial on its own when
         * anything better exists.
         */
        SUBSTRING,
    }

    fun tierOf(contactName: String, spoken: String): Tier {
        val name = fold(contactName)
        val said = fold(spoken)
        return when {
            name == said -> Tier.EXACT
            name.split(' ').any { it.startsWith(said) } -> Tier.WORD_PREFIX
            else -> Tier.SUBSTRING
        }
    }

    /**
     * Picks the outcome for [matches] against what the driver said.
     *
     * [ContactLookupResult.Found] means "dial this, now" — so it is only
     * returned when the best tier holds a single person. One person reachable
     * on several *distinguishable* numbers is a question ("¿el móvil o el del
     * trabajo?"); numbers RoadMate cannot name apart are not worth asking
     * about, so the first is used.
     */
    fun resolve(matches: List<ContactMatch>, spoken: String): ContactLookupResult {
        if (matches.isEmpty()) return ContactLookupResult.NotFound

        val best = matches
            .groupBy { tierOf(it.name, spoken) }
            .minByOrNull { it.key.ordinal }
            ?.value
            ?: return ContactLookupResult.NotFound

        if (best.size == 1) return ContactLookupResult.Found(best.first())

        val onePerson = best.map { fold(it.name) }.distinct().size == 1
        if (!onePerson) return ContactLookupResult.Ambiguous(best)

        val namable = best.map { it.label }.filter { it != PhoneLabel.OTHER }.distinct()
        return if (namable.size >= 2) ContactLookupResult.Ambiguous(best)
        else ContactLookupResult.Found(best.first())
    }

    /** Lower-case and strip accents, so "garcia" answers to "García". */
    fun fold(value: String): String =
        Normalizer.normalize(value.lowercase().trim(), Normalizer.Form.NFD)
            .replace(ACCENT_MARKS, "")

    /**
     * Digits (and a leading +) only, so the same line stored twice — once by
     * the SIM as "600 11 22 33" and once by an account as "+34600112233" —
     * doesn't look like two numbers to choose between.
     */
    fun normalizeNumber(number: String): String = number.filter { it.isDigit() || it == '+' }

    private val ACCENT_MARKS = spanishRegex("\\p{Mn}+", ignoreCase = false)
}
