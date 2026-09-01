package dev.pgm.roadmate.domain.model

/**
 * How long RoadMate's spoken answers should be. Set by voice ("respuestas
 * cortas", "respuestas normales", "con más detalle"), remembered across
 * trips, and folded into every Gemini prompt. The first bit of the app
 * adapting to the driver rather than answering everyone the same way.
 */
enum class AnswerStyle(
    /** Dropped into the prompt in place of the default length instruction. */
    val promptInstruction: String,
    /** Spoken back when the driver switches to this style. */
    val spokenAck: String,
) {
    BRIEF("Responde en una sola frase, sin rodeos.", "Vale, seré más breve."),
    NORMAL("Responde en 1-2 frases.", "Vale, respuestas normales."),
    DETAILED(
        "Responde en 3 o 4 frases, con algo más de contexto útil, sin irte por las ramas.",
        "Vale, daré algo más de detalle.",
    ),
    ;

    companion object {
        val DEFAULT = NORMAL
    }
}
