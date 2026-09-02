package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.LocalAiStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Contract for asking the on-device model a question and getting text back.
 *
 * Answers are produced by whichever local backend is usable, in order:
 * AICore / Gemini Nano, then the downloadable model (MediaPipe), then a
 * canned "modo básico" fallback string. Callers don't choose — they just ask.
 */
interface GeminiRepository {

    suspend fun getResponse(prompt: String): String

    /**
     * Streaming form of [getResponse]: emits the answer as the model produces
     * it, so a caller can start speaking the first sentence without waiting
     * for the whole reply. Every emission is the full text so far (cumulative)
     * and the last one is the complete answer. Backends that can't stream —
     * AICore today, the "modo básico" fallback, a cache hit — emit once.
     *
     * Default implementation just wraps [getResponse]; the real backend
     * ([dev.pgm.roadmate.data.repository.GeminiRepositoryImpl]) overrides it.
     */
    fun getResponseStream(prompt: String): Flow<String> = flow { emit(getResponse(prompt)) }

    /**
     * Pre-loads the downloaded model so the first real question doesn't pay
     * the ~10 s cold-start cost. No-op when AICore is available or no model
     * is present. Safe to call from a background scope at startup.
     */
    suspend fun warmUp()

    /** Clears any cached responses — call when a new trip starts. */
    fun clearCache()

    /**
     * Live state of on-device AI for the UI: whether a local backend is
     * ready, whether the model is downloading, waiting for Wi-Fi, or the
     * device is stuck in "modo básico". Lets the UI be upfront instead of
     * the user only finding out via generic fallback text.
     */
    fun localAiStatus(): Flow<LocalAiStatus>

    /**
     * Kicks off the Wi-Fi-only HTTPS download of the local model file.
     * Progress is reported through [localAiStatus]. No-op when a local
     * backend is already available or the download path is disabled.
     */
    suspend fun requestLocalAiModelDownload()
}
