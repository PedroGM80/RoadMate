package dev.pgm.roadmate.utils

/**
 * Tidies a place label before it's stored as a PLACE fact, so a voice search
 * ("una gasolinera") and a map-pin tap ("Gasolinera Repsol") don't pile up as
 * near-duplicates. Lower-cased, article-stripped, whitespace-collapsed,
 * length-capped.
 */
object PlaceName {

    private val LEADING_ARTICLE = Regex("""^(?:un|una|unos|unas|el|la|los|las)\s+""", RegexOption.IGNORE_CASE)
    private const val MAX = 48

    fun normalize(raw: String): String =
        raw.trim()
            .replace(Regex("""\s+"""), " ")
            .replace(LEADING_ARTICLE, "")
            .lowercase()
            .take(MAX)
            .trim()
}
