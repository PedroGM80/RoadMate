package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.ml.DebugTrace
import dev.pgm.roadmate.ml.GeminiNanoManager
import dev.pgm.roadmate.ml.LocalAiModelManager
import dev.pgm.roadmate.ml.LocalLlmManager
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

    private val responseCache = mutableMapOf<String, String>()

    override suspend fun getResponse(prompt: String): String {
        responseCache[prompt]?.let {
            DebugTrace.log("GEMINI cache hit")
            return it
        }
        val response = generate(prompt)
        responseCache[prompt] = response
        return response
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

    override fun clearCache() {
        responseCache.clear()
    }

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
}
