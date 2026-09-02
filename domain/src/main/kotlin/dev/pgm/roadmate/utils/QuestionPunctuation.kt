package dev.pgm.roadmate.utils

/**
 * Vosk returns bare lowercase text with no punctuation ("cuántos kilómetros
 * faltan"). When the utterance clearly starts as a question, wrap it in
 * `¿…?` so it reads right on screen and gives the model a clearer signal.
 * Conservative on purpose — only fires on an unmistakable interrogative
 * opener, never on a statement.
 */
object QuestionPunctuation {

    private val INTERROGATIVE_OPENER = spanishRegex(
        """^(?:qu[eé]|cu[aá]l(?:es)?|c[oó]mo|cu[aá]ndo|d[oó]nde|ad[oó]nde|""" +
            """qui[eé]n(?:es)?|cu[aá]nt[oa]s?|por\s+qu[eé]|para\s+qu[eé]|""" +
            """hay\s+(?:alg[uú]n|alguna)|se\s+puede|puedo|podr[íi]as?|""" +
            """est[aá]\s+(?:lejos|cerca|abierto)|falta\s+mucho|queda\s+mucho)\b""",
    )

    fun normalize(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty() || t.startsWith("¿") || t.endsWith("?")) return t
        return if (INTERROGATIVE_OPENER.containsMatchIn(t)) "¿$t?" else t
    }
}
