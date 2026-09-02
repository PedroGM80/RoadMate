package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.LocalAiCatalog
import dev.pgm.roadmate.domain.model.LocalAiModel
import dev.pgm.roadmate.domain.model.LocalAiStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

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
     * Hands the local model's memory back to the system.
     *
     * The loaded model is by far the largest thing in RoadMate's process
     * (0.5–1.6 GB depending on which one is configured), so when Android
     * reports memory pressure this is what should be given up: the cost is one
     * cold start on the next question, against the app being killed outright
     * mid-trip. Returns true if anything was actually released — a generation
     * in flight is left alone, and trim requests are advisory, so a false here
     * is a normal outcome, not a failure.
     */
    suspend fun releaseLocalAiMemory(): Boolean = false

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

    /** The downloadable models the driver can choose between. */
    val localAiModels: List<LocalAiModel> get() = LocalAiCatalog.models

    /** Id of the model currently selected for the downloadable backend. */
    fun selectedLocalAiModelId(): Flow<String> = flowOf(LocalAiCatalog.recommended.id)

    /**
     * Persist a different model choice and start swapping to it: the new file
     * downloads (Wi-Fi only), the previous one is removed, and the loaded
     * engine is dropped so the next question uses the new model. No-op for an
     * unknown id.
     */
    suspend fun selectLocalAiModel(id: String) {}
}
