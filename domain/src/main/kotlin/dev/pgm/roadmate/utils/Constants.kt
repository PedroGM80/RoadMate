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
        "Eres el copiloto de viaje local de RoadMate. Respondes de forma breve, " +
            "clara y útil a un conductor, usando su ubicación y contexto de viaje. " +
            "Nunca sugieras acciones que distraigan de la conducción."

    val REST_REMINDER_PROMPTS = listOf(
        "¿Necesitas descansar?",
        "Cuéntame datos curiosos del sitio en el que estoy."
    )

    /** Spoken once per calendar day, the first time the app is opened with core permissions granted. */
    fun greetingForHour(hour: Int): String = when (hour) {
        in 6..12 -> "Buenos días. Soy RoadMate, listo para la carretera."
        in 13..19 -> "Buenas tardes. Soy RoadMate, ¿rodamos?"
        else -> "Buenas noches. Soy RoadMate, conduce con cuidado."
    }
}
