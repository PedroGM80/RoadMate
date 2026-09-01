package dev.pgm.roadmate.ml

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs prompts through the downloaded model via the MediaPipe LLM Inference
 * API. This is the universal local-AI fallback — used only when AICore /
 * Gemini Nano is unavailable and [LocalAiModelManager] reports the model
 * file present on disk.
 *
 * The `LlmInference` engine is heavy (loads ~550 MB), so it's created once
 * and kept; a fresh short-lived session is used per question. Generation is
 * off-loaded to a background dispatcher and bounded by
 * [Constants.LOCAL_LLM_TIMEOUT_MS]; on timeout or any failure this returns
 * null and the caller falls back to the canned "modo básico" text.
 *
 * `com.google.mediapipe:tasks-genai` moves fast — verify the builder/session
 * signatures against the version in the catalog before shipping.
 */
@Singleton
class LocalLlmManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: LocalAiModelManager
) {

    @Volatile
    private var engine: LlmInference? = null

    private fun obtainEngine(): LlmInference? {
        engine?.let { return it }
        val modelPath = modelManager.modelFile()?.absolutePath ?: return null
        return synchronized(this) {
            engine ?: runCatching {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .build()
                LlmInference.createFromOptions(context, options)
            }.onFailure { Log.w(TAG, "Failed to create LlmInference", it) }
                .getOrNull()
                ?.also { engine = it }
        }
    }

    suspend fun isReady(): Boolean = withContext(Dispatchers.Default) { obtainEngine() != null }

    /** Generated text, or null if the model isn't usable / timed out / failed. */
    suspend fun generateResponse(prompt: String): String? = withContext(Dispatchers.Default) {
        val llm = obtainEngine() ?: return@withContext null
        withTimeoutOrNull(Constants.LOCAL_LLM_TIMEOUT_MS) {
            runCatching {
                val session = LlmInferenceSession.createFromOptions(
                    llm,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(TOP_K)
                        .setTemperature(TEMPERATURE)
                        .build()
                )
                try {
                    session.addQueryChunk(prompt)
                    session.generateResponse()
                } finally {
                    session.close()
                }
            }.onFailure { Log.w(TAG, "generateResponse failed", it) }.getOrNull()
        }?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val MAX_TOKENS = 512
        const val TOP_K = 40
        const val TEMPERATURE = 0.2f
        const val TAG = "LocalLlmManager"
    }
}
