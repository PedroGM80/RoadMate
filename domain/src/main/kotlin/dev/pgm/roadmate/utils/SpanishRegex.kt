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
 *
 * This function never throws. A pattern that the running engine won't compile
 * (the `(?U)` prefix on ICU was exactly this — an `ExceptionInInitializerError`
 * that took the whole app down at launch) falls back to the bare pattern, then
 * to a matches-nothing regex. A parser that quietly stops recognising one
 * phrasing is a bug; a parser that crashes the copilot mid-drive is a hazard.
 */
private val UNICODE_CLASS_PREFIX: String =
    runCatching { Regex("(?U)x"); "(?U)" }.getOrDefault("")

/** Compiles to nothing a real utterance can contain, so a failed pattern is inert. */
private const val MATCHES_NOTHING = "(?!x)x"

fun spanishRegex(pattern: String, ignoreCase: Boolean = true): Regex {
    val opts = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
    return runCatching { Regex(UNICODE_CLASS_PREFIX + pattern, opts) }
        .recoverCatching { Regex(pattern, opts) }
        .recoverCatching {
            System.err.println("spanishRegex: pattern won't compile on this engine: $pattern")
            Regex(MATCHES_NOTHING)
        }
        .getOrThrow()
}
