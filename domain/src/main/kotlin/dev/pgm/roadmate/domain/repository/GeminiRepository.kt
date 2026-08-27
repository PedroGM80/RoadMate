package dev.pgm.roadmate.domain.repository

/**
 * Contract for asking the on-device model a question and getting text back.
 */
interface GeminiRepository {

    suspend fun getResponse(prompt: String): String

    /** Clears any cached responses — call when a new trip starts. */
    fun clearCache()

    /**
     * Whether the on-device model is actually usable on this hardware. Lets
     * the UI be upfront about running in a reduced "modo básico" instead of
     * silently returning generic fallback text with no explanation.
     */
    suspend fun isLocalAiAvailable(): Boolean
}
