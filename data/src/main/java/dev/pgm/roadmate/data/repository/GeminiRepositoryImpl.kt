package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.ml.DebugTrace
import dev.pgm.roadmate.ml.GeminiNanoManager
import dev.pgm.roadmate.ml.LocalAiModelManager
import dev.pgm.roadmate.ml.LocalLlmManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a prompt to the best available local backend and caches the answer
 * for the trip so repeating a question doesn't re-run inference:
 *
 *  1. **AICore / Gemini Nano** ([GeminiNanoManager]) when the hardware has it.
 *  2. **Downloaded model** ([LocalLlmManager]) once [LocalAiModelManager]
 *     has fetched it — the universal fallback for devices without AICore.
 *  3. Canned "modo básico" text ([GeminiNanoManager.FALLBACK_RESPONSE]) when
 *     neither is available.
 */
@Singleton
class GeminiRepositoryImpl @Inject constructor(
    private val geminiNanoManager: GeminiNanoManager,
    private val localAiModelManager: LocalAiModelManager,
    private val localLlmManager: LocalLlmManager
) : GeminiRepository {

    /**
     * Answer cache, keyed on the whole prompt.
     *
     * Bounded and synchronized on purpose. Prompts embed the clock and the GPS
     * fix, so nearly every question produces a fresh key and the old unbounded
     * map grew for the length of a trip. And it is read/written from at least
     * two threads — the ViewModel's main-dispatcher loop and the background
     * wake-word service's IO scope — where a plain `mutableMapOf` can corrupt
     * or throw. Insertion-ordered, oldest evicted first.
     */
    private val responseCache = LinkedHashMap<String, String>()

    private fun cachedAnswer(prompt: String): String? = synchronized(responseCache) {
        responseCache[prompt]
    }

    private fun cacheAnswer(prompt: String, answer: String) = synchronized(responseCache) {
        responseCache[prompt] = answer
        while (responseCache.size > MAX_CACHED_ANSWERS) {
            val oldest = responseCache.keys.firstOrNull() ?: break
            responseCache.remove(oldest)
        }
    }

    override suspend fun getResponse(prompt: String): String {
        cachedAnswer(prompt)?.let {
            DebugTrace.log("GEMINI cache hit")
            return it
        }
        val response = generate(prompt)
        cacheAnswer(prompt, response)
        return response
    }

    /**
     * Streaming path. Only the downloaded MediaPipe model streams for real;
     * AICore and the "modo básico" fallback emit a single cumulative value so
     * every caller can treat this uniformly. A completed answer is cached the
     * same as [getResponse]; a run that produced nothing ends on the fallback
     * string.
     */
    override fun getResponseStream(prompt: String): Flow<String> = flow {
        cachedAnswer(prompt)?.let {
            DebugTrace.log("GEMINI cache hit (stream)")
            emit(it)
            return@flow
        }

        if (geminiNanoManager.checkAvailability()) {
            DebugTrace.log("GEMINI stream backend = AICore/Nano (one-shot)")
            val full = geminiNanoManager.generateResponse(prompt)
            cacheAnswer(prompt, full)
            emit(full)
            return@flow
        }

        if (localLlmManager.isReady()) {
            DebugTrace.log("GEMINI stream backend = LocalLlm (downloaded model)")
            var last = ""
            try {
                localLlmManager.generateResponseStream(prompt).collect { cumulative ->
                    last = cumulative
                    emit(cumulative)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                DebugTrace.log("GEMINI stream error: ${t.message}")
            }
            if (last.isNotBlank()) {
                cacheAnswer(prompt, last)
            } else {
                DebugTrace.log("GEMINI stream produced nothing -> FALLBACK")
                emit(GeminiNanoManager.FALLBACK_RESPONSE)
            }
            return@flow
        }

        DebugTrace.log("GEMINI stream backend = FALLBACK (no AICore, model not ready)")
        emit(GeminiNanoManager.FALLBACK_RESPONSE)
    }

    private suspend fun generate(prompt: String): String {
        if (geminiNanoManager.checkAvailability()) {
            DebugTrace.log("GEMINI backend = AICore/Nano")
            return geminiNanoManager.generateResponse(prompt)
        }
        if (localLlmManager.isReady()) {
            DebugTrace.log("GEMINI backend = LocalLlm (downloaded model)")
            localLlmManager.generateResponse(prompt)?.let { return it }
            DebugTrace.log("GEMINI LocalLlm returned null -> FALLBACK")
        } else {
            DebugTrace.log("GEMINI backend = FALLBACK (no AICore, model not ready)")
        }
        return GeminiNanoManager.FALLBACK_RESPONSE
    }

    override suspend fun warmUp() {
        if (geminiNanoManager.checkAvailability()) return
        localLlmManager.warmUp()
    }

    override fun clearCache() = synchronized(responseCache) { responseCache.clear() }

    override fun localAiStatus(): Flow<LocalAiStatus> = flow {
        emit(LocalAiStatus.Checking)
        if (geminiNanoManager.checkAvailability()) {
            emit(LocalAiStatus.ReadyAicore)
            return@flow
        }
        localAiModelManager.refreshStatus()
        emitAll(localAiModelManager.status)
    }

    override suspend fun requestLocalAiModelDownload() {
        localAiModelManager.fetch()
    }

    private companion object {
        /** Enough to catch a repeated question in one trip, small enough to forget. */
        const val MAX_CACHED_ANSWERS = 32
    }
}
