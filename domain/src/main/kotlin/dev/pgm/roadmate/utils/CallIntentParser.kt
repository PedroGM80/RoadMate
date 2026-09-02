package dev.pgm.roadmate.utils

/**
 * Extracts a contact name from a "llama a X" / "llamar a X" / "telefonea a
 * X" style request. Pure text matching, no Android dependency — checked
 * before Gemini in GenerateResponseUseCase, same shortcut pattern as
 * JokeProvider.
 */
object CallIntentParser {

    private val PATTERNS = listOf(
        spanishRegex("""(?:llama|llamar|telefonea|telefonear)\s+a\s+(.+)""")
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
