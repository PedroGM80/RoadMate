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
import java.io.File
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

    init {
        DebugTrace.init(File(context.filesDir, "aicore_debug.log"))
    }

    private fun dbg(line: String) = DebugTrace.log("LLM $line")

    private fun obtainEngine(): LlmInference? {
        engine?.let { return it }
        val modelPath = modelManager.modelFile()?.absolutePath ?: return null
        return synchronized(this) { engine ?: buildEngine(modelPath) }
    }

    private fun buildEngine(modelPath: String): LlmInference? = runCatching {
        val t0 = System.currentTimeMillis()
        // CPU only. GPU backend init on arbitrary Android GPUs via MediaPipe
        // is unreliable (silent hangs) and this path already isn't fast-path.
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .build()
        LlmInference.createFromOptions(context, options).also {
            dbg("engine ready (${System.currentTimeMillis() - t0} ms)")
        }
    }.onFailure { dbg("engine build failed: ${it.message}") }
        .getOrNull()
        ?.also { engine = it }

    suspend fun isReady(): Boolean = withContext(Dispatchers.Default) { obtainEngine() != null }

    /** Build the engine and run one throwaway generation so the first real
     *  question doesn't eat the ~10 s cold XNNPACK/prefill cost. */
    suspend fun warmUp() = withContext(Dispatchers.Default) {
        if (engine != null) return@withContext
        runCatching {
            val t0 = System.currentTimeMillis()
            generateResponse("Di \"listo\".")
            dbg("warm-up done (${System.currentTimeMillis() - t0} ms)")
        }
        Unit
    }

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
                    // Keep MediaPipe's native tokenizer away from control
                    // chars and pathological lengths — a garbage/huge prompt
                    // has crashed nativePredictSync with a JNI abort on-device.
                    // The .task bundle applies its own chat template, so send
                    // clean plain text, not hand-rolled ChatML markers.
                    val safe = prompt
                        .replace(Regex("[\\p{Cntrl}&&[^\n]]"), " ")
                        .take(MAX_PROMPT_CHARS)
                    dbg("PROMPT (${safe.length} chars) >>>\n$safe")
                    session.addQueryChunk(safe)
                    val t0 = System.currentTimeMillis()
                    session.generateResponse().fixMojibake().also {
                        dbg("RESPONSE (${System.currentTimeMillis() - t0} ms) <<< \"$it\"")
                    }
                } finally {
                    session.close()
                }
            }.onFailure {
                Log.w(TAG, "generateResponse failed", it)
                dbg("generateResponse FAILED: ${it.stackTraceToString()}")
            }.getOrNull()
        }?.takeIf { it.isNotBlank() }
    }

    /**
     * MediaPipe `tasks-genai` hands back the model's UTF-8 output decoded as
     * Latin-1, so "kilómetros" arrives as "kilÃ³metros". If the string carries
     * that signature, round-trip the bytes back through UTF-8.
     */
    private fun String?.fixMojibake(): String? {
        val s = this ?: return null
        if (!s.contains('Ã') && !s.contains('Â')) return s
        return runCatching {
            String(s.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }.getOrDefault(s)
    }

    private companion object {
        const val MAX_TOKENS = 512
        const val TOP_K = 40
        const val TEMPERATURE = 0.2f

        /** Hard ceiling on prompt length fed to the native engine. Well under
         *  the model's KV window; PromptBuilder already caps to a similar size. */
        const val MAX_PROMPT_CHARS = 1400
        const val TAG = "LocalLlmManager"
    }
}
