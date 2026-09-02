package dev.pgm.roadmate.utils

/**
 * App-wide tunable constants for silence detection and Gemini prompting.
 *
 * End-of-utterance for a voice question is Vosk's own end-pointing (see
 * [dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository]), not a
 * constant here. The one silence timeout that *is* tunable is the long
 * rest-break one.
 */
object Constants {

    const val SILENCE_THRESHOLD_DB = -50.0

    /** Cabin silence duration that triggers a rest-break prompt (DetectSilenceUseCase). */
    const val REST_REMINDER_SILENCE_MS = 30 * 60 * 1_000L

    const val MAX_CONTEXT_LENGTH = 2_000

    const val GEMINI_TIMEOUT_MS = 5_000L

    /**
     * Timeout for a single generation on the downloaded model (MediaPipe).
     * Much longer than [GEMINI_TIMEOUT_MS] — a CPU-backend sub-2B model on
     * mid-range hardware is far slower than AICore, and this path only runs
     * when there's no faster option anyway.
     */
    const val LOCAL_LLM_TIMEOUT_MS = 20_000L

    /** How long to wait for a GPS fix before showing "location unavailable" instead of spinning forever. */
    const val LOCATION_TIMEOUT_MS = 10_000L

    const val GEMINI_SYSTEM_PROMPT =
        "Eres RoadMate, copiloto de a bordo. Responde en español, 1-2 frases " +
            "cortas, sin saludos ni repetir la pregunta. Responde lo que " +
            "sepas. No inventes datos del trayecto (distancia, destino, " +
            "tráfico, hora de llegada): si no están en el Contexto, dilo."

    val REST_REMINDER_PROMPTS = listOf(
        "¿Debería parar a descansar?",
        "Llevo mucho rato conduciendo. ¿Qué me recomiendas?"
    )

    /** Spoken once per calendar day, the first time the app is opened with core permissions granted. */
    fun greetingForHour(hour: Int): String = when (hour) {
        in 6..12 -> "Buenos días. RoadMate en marcha."
        in 13..19 -> "Buenas tardes. Aquí RoadMate, listo."
        else -> "Buenas noches. RoadMate contigo. Atento a la carretera."
    }
}
