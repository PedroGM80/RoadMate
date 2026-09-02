package dev.pgm.roadmate.utils

/**
 * Builds a [Regex] that actually works on Spanish.
 *
 * `\b`, `\w` and `\W` — and case folding — have to be Unicode-aware or most
 * of the language slips through:
 *
 *  - `qu[eé]\b` never matched "qué hora es". "é" is not an ASCII word
 *    character, so between "é" and the space there is no boundary to find —
 *    and "qué" is the single most common way a driver starts a question.
 *  - `\b[uú]ltim[oa]\b` never matched "la última", for the same reason at the
 *    front of the word.
 *  - `split(Regex("\\W+"))` on "Ana García" produced "Ana", "Garc", "a",
 *    so matching a contact by surname quietly failed on any accented name.
 *  - ASCII-only `IGNORE_CASE` means "QUÉ" did not match "qué" either.
 *
 * The two regex engines this runs on disagree on how to ask for that:
 *
 *  - the JVM ([java.util.regex], used by `:domain`'s unit tests) is ASCII-only
 *    for `\w`/`\b` unless you pass the `(?U)` inline flag
 *    (`UNICODE_CHARACTER_CLASS`), which also turns on Unicode case folding.
 *  - Android's engine is ICU, whose `\w`/`\b` and case folding are already
 *    Unicode by default — and which rejects `(?U)` as a syntax error
 *    (a crash at first use, not a fallback).
 *
 * So probe once for `(?U)` support and only prepend it where it's both needed
 * and accepted. Prefer this over `Regex(...)` for anything that reads what the
 * driver said.
 */
private val UNICODE_CLASS_PREFIX: String =
    runCatching { Regex("(?U)x"); "(?U)" }.getOrDefault("")

fun spanishRegex(pattern: String, ignoreCase: Boolean = true): Regex =
    Regex(
        UNICODE_CLASS_PREFIX + pattern,
        if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
    )
