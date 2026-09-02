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

    /**
     * The same instruction without the word "respuestas", which is how people
     * actually say it — "con más detalle", "sé más breve", "más corto". The
     * README has advertised these all along and none of them worked.
     *
     * Anchored to the *whole* utterance, or to an imperative aimed at
     * RoadMate ("sé/habla/responde/contéstame más…"). That is what keeps the
     * original guard intact: "cuéntame algo corto de este pueblo" is a
     * request for content and must still reach the model, and it matches
     * neither form.
     */
    private val BARE_PATTERN = spanishRegex(
        """^(?:(?:s[eé]|habla|responde|cont[eé]stame|resp[oó]ndeme|contesta)\s+)?""" +
            """(?:un\s+poco\s+)?(?:con\s+)?m[aá]s\s+""" +
            """(cort[ao]s?|brev(?:e|es)|concis[ao]s?|resumid[ao]s?|larg[ao]s?|detalle|detallad[ao]s?)""" +
            """\s*[.!]?$""",
    )

    fun parse(userInput: String): AnswerStyle? {
        val text = userInput.trim()
        val hit = (PATTERN.find(text) ?: BARE_PATTERN.find(text))
            ?.groupValues?.get(1)?.lowercase() ?: return null
        return when {
            hit.startsWith("cort") || hit.startsWith("brev") ||
                hit.startsWith("resum") || hit.startsWith("concis") -> AnswerStyle.BRIEF
            hit.startsWith("larg") || hit.startsWith("detall") || hit.contains("detalle") -> AnswerStyle.DETAILED
            else -> AnswerStyle.NORMAL
        }
    }
}
