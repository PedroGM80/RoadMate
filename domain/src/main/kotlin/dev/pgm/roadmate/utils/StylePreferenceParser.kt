package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.AnswerStyle

/**
 * Recognizes the driver telling RoadMate how long they want answers:
 * "respuestas cortas", "respuestas un poco más breves", "respuestas con más
 * detalle", "respuestas normales"… Checked before Gemini in
 * GenerateResponseUseCase — same shortcut pattern as [CallIntentParser]. A
 * match is a setting change, not a question.
 *
 * Deliberately anchored on the word "respuesta(s)" so it doesn't fire on a
 * genuine request like "cuéntame algo corto de este pueblo".
 */
object StylePreferenceParser {

    private val PATTERN = spanishRegex(
        """respuestas?\s+(?:(?:un\s+poco\s+)?m[aá]s\s+)?""" +
            """(cortas?|breves?|resumidas?|larg[ao]s?|detallad[ao]s?|con\s+m[aá]s\s+detalle|con\s+detalle|normales?|est[aá]ndar)""",
    )

    fun parse(userInput: String): AnswerStyle? {
        val hit = PATTERN.find(userInput.trim())?.groupValues?.get(1)?.lowercase() ?: return null
        return when {
            hit.startsWith("cort") || hit.startsWith("brev") || hit.startsWith("resum") -> AnswerStyle.BRIEF
            hit.startsWith("larg") || hit.startsWith("detall") || hit.contains("detalle") -> AnswerStyle.DETAILED
            else -> AnswerStyle.NORMAL
        }
    }
}
