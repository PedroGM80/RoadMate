package dev.pgm.roadmate.utils

/**
 * App-wide tunable constants for silence detection and Gemini prompting.
 *
 * The spec's SILENCE_DURATION_MS = 30000 with a "(30 min)" comment was
 * self-contradictory (30000ms is 30 seconds). Two different silence
 * timeouts are actually needed for two different purposes, so they're
 * split here instead of collapsed into one constant:
 *  - RECORDING_END_SILENCE_MS: short pause that means "user stopped talking".
 *  - REST_REMINDER_SILENCE_MS: long silence that means "suggest a break".
 */
object Constants {

    const val SILENCE_THRESHOLD_DB = -50.0

    /** Silence gap that ends a voice-input recording (RecordAudioUseCase). */
    const val RECORDING_END_SILENCE_MS = 5_000L

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
        "Eres el copiloto de RoadMate y hablas a un conductor. Contesta " +
            "directamente a su pregunta, en español, con frases cortas y " +
            "claras y tono tranquilo. Nada de saludos, disculpas ni repetir " +
            "la pregunta. Si no sabes algo, dilo en una frase. Usa el " +
            "contexto del viaje solo si ayuda a responder."

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
