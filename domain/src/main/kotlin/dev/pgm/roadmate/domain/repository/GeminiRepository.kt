package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.LocalAiStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contract for asking the on-device model a question and getting text back.
 *
 * Answers are produced by whichever local backend is usable, in order:
 * AICore / Gemini Nano, then the downloadable model (MediaPipe), then a
 * canned "modo básico" fallback string. Callers don't choose — they just ask.
 */
interface GeminiRepository {

    suspend fun getResponse(prompt: String): String

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
