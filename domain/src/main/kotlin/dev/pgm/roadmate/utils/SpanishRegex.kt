package dev.pgm.roadmate.utils

/**
 * Builds a [Regex] that actually works on Spanish.
 *
 * Java's regex engine — which Kotlin's [Regex] is — defines `\b`, `\w` and
 * `\W` over ASCII unless you ask otherwise. In Spanish that is not a rounding
 * error, it is most of the language:
 *
 *  - `qu[eé]\b` never matched "qué hora es". "é" is not an ASCII word
 *    character, so between "é" and the space there is no boundary to find —
 *    and "qué" is the single most common way a driver starts a question.
 *  - `\b[uú]ltim[oa]\b` never matched "la última", for the same reason at the
 *    front of the word.
 *  - `split(Regex("\\W+"))` on "Ana García" produced "Ana", "Garc", "a",
 *    so matching a contact by surname quietly failed on any accented name.
 *  - `IGNORE_CASE` alone is ASCII-only case folding, so "QUÉ" did not match
 *    "qué" either.
 *
 * The `(?U)` flag (Java's `UNICODE_CHARACTER_CLASS`) redefines the character
 * classes over Unicode and turns on Unicode-aware case folding with them, so
 * every pattern built here gets both fixes. Prefer this over `Regex(...)` for
 * anything that reads what the driver said.
 */
fun spanishRegex(pattern: String, ignoreCase: Boolean = true): Regex =
    Regex(
        "(?U)$pattern",
        if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
    )
