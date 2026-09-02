package dev.pgm.roadmate.utils

/**
 * Extracts a contact name from a "llama a X" / "llamar a X" / "telefonea a
 * X" style request. Pure text matching, no Android dependency — checked
 * before Gemini in GenerateResponseUseCase, same shortcut pattern as
 * JokeProvider.
 */
object CallIntentParser {

    private val PATTERNS = listOf(
        // "llama a Ana", "quiero llamar a mamá", "llámame a Ana" (Vosk hears
        // the pronoun that isn't there often enough to be worth accepting).
        spanishRegex("""(?:ll[aá]ma(?:me|le)?|llamar|telefon[eé]a(?:le)?|telefonear)\s+a\s+(.+)"""),
        // "marca el número de Ana", "marca a Ana"
        spanishRegex("""marca(?:r)?\s+(?:el\s+(?:n[uú]mero|tel[eé]fono)\s+de\s+|a\s+)(.+)"""),
        // "ponme con Ana", "pásame con Ana" — the "con" is what keeps this
        // clear of MediaIntentParser's "ponme música".
        spanishRegex("""(?:ponme|p[aá]same|comun[ií]came)\s+con\s+(.+)"""),
        // "quiero hablar con Ana"
        spanishRegex("""(?:quiero\s+)?hablar\s+con\s+(.+)"""),
    )

    fun extractContactName(userInput: String): String? {
        val trimmedInput = userInput.trim()
        for (pattern in PATTERNS) {
            val match = pattern.find(trimmedInput) ?: continue
            val name = match.groupValues[1].trim().trimEnd('.', '?', '!', ' ')
            if (name.isNotBlank()) return name
        }
        return null
    }
}
