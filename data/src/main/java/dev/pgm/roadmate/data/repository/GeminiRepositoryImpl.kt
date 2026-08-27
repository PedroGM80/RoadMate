package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.ml.GeminiNanoManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [GeminiNanoManager] with an in-memory response cache so repeating the
 * same question within a trip doesn't re-run inference.
 */
@Singleton
class GeminiRepositoryImpl @Inject constructor(
    private val geminiNanoManager: GeminiNanoManager
) : GeminiRepository {

    private val responseCache = mutableMapOf<String, String>()

    override suspend fun getResponse(prompt: String): String {
        responseCache[prompt]?.let { return it }
        val response = geminiNanoManager.generateResponse(prompt)
        responseCache[prompt] = response
        return response
    }

    override fun clearCache() {
        responseCache.clear()
    }

    override suspend fun isLocalAiAvailable(): Boolean = geminiNanoManager.checkAvailability()
}
