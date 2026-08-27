package dev.pgm.roadmate.domain.repository

/**
 * Contract for asking the on-device model a question and getting text back.
 */
interface GeminiRepository {

    suspend fun getResponse(prompt: String): String

    /** Clears any cached responses — call when a new trip starts. */
    fun clearCache()
}
