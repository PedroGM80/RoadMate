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

    const val GEMINI_SYSTEM_PROMPT =
        "Eres el copiloto de viaje local de RoadMate. Respondes de forma breve, " +
            "clara y útil a un conductor, usando su ubicación y contexto de viaje. " +
            "Nunca sugieras acciones que distraigan de la conducción."

    val REST_REMINDER_PROMPTS = listOf(
        "¿Necesitas descansar?",
        "Cuéntame datos curiosos del sitio en el que estoy."
    )
}
