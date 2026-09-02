package dev.pgm.roadmate.utils

/**
 * "¿dónde estoy?", "¿qué carretera es esta?", "¿por dónde voy?" — the driver
 * asking where they are right now. When the offline map has resolved a
 * street/locality this is answered straight from it, no model call.
 */
object LocationQuestionParser {

    private val PATTERN = spanishRegex(
        """d[oó]nde\s+(?:estoy|estamos|me\s+encuentro)|""" +
            """(?:en\s+)?qu[eé]\s+(?:calle|carretera|v[ií]a|autov[ií]a|autopista|nacional|""" +
            """comarcal|zona|barrio|pueblo|ciudad|sitio)\s+(?:es\s+esta|estoy|voy|vamos|estamos|ando)|""" +
            """por\s+d[oó]nde\s+(?:voy|vamos|ando|circulo)|""" +
            """dime\s+(?:la\s+calle|d[oó]nde\s+estoy|mi\s+ubicaci[oó]n)|""" +
            """mi\s+ubicaci[oó]n\s+actual|cu[aá]l\s+es\s+mi\s+ubicaci[oó]n""",
    )

    fun matches(userInput: String): Boolean = PATTERN.containsMatchIn(userInput.trim())
}
