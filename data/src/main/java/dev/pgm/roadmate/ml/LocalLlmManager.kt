package dev.pgm.roadmate.ml

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
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
 * off-loaded to [inferenceDispatcher] — one dedicated thread, so a
 * multi-second CPU-bound run can't pin a shared `Dispatchers.Default` worker
 * and starve unrelated coroutines — and bounded by
 * [Constants.LOCAL_LLM_TIMEOUT_MS]; on timeout or any failure this returns
 * null and the caller falls back to the canned "modo básico" text.
 *
 * [generateResponseStream] is the same generation surfaced token-batch by
 * token-batch, so callers can speak the first sentence while the rest is
 * still being produced.
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

    /**
     * Generations currently using [engine].
     *
     * [generateResponseStream] hands work to MediaPipe's own thread and
     * returns, so the inference dispatcher being free does *not* mean the
     * engine is idle. Closing it while native code is still producing tokens
     * is a use-after-free, so [releaseEngine] checks this first.
     */
    private val inFlight = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * One thread, dedicated to inference. A generation is a several-second,
     * fully CPU-bound native call; on the shared `Dispatchers.Default` pool it
     * would hold a worker for that entire time and stall unrelated coroutine
     * work. Process-lived (this is a [Singleton]), so it's never shut down.
     */
    private val inferenceDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "roadmate-llm").apply { isDaemon = true } }
            .asCoroutineDispatcher()

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

    /**
     * Whether this backend can answer — deliberately *not* run on
     * [inferenceDispatcher].
     *
     * That dispatcher is one thread and a generation holds it for seconds, so
     * hopping onto it here made a routing question ("which backend do I use?")
     * queue behind whatever answer was already being produced — including the
     * startup warm-up. The check itself needs no engine: an engine already
     * built is ready, and otherwise the model file being on disk is what
     * decides. If the build then fails on the inference thread, the callers
     * already handle it (a null answer / an empty stream both fall through to
     * "modo básico"), so nothing is lost by answering optimistically here.
     *
     * Still suspend, and still off the caller's thread: the fallback check
     * stats the model file, and the callers collect on the main dispatcher.
     */
    suspend fun isReady(): Boolean =
        engine != null || withContext(Dispatchers.IO) { modelManager.modelFile() != null }

    /**
     * Drops the loaded model and gives its memory back.
     *
     * The engine holds the whole model resident — 0.5 GB for Qwen2.5-0.5B,
     * ~1.6 GB for the 1.5B build Pedro actually runs. That is far and away the
     * largest thing in the process, so when Android says it is short of
     * memory, this is what should go: otherwise the OS kills RoadMate outright
     * and the driver loses the assistant mid-trip, rather than paying one cold
     * start on the next question.
     *
     * Runs on [inferenceDispatcher] so it serialises behind anything already
     * queued there, and refuses while a generation is in flight — a streaming
     * run continues on MediaPipe's thread after this dispatcher is free again,
     * and closing under it would crash natively. Trim requests are advisory,
     * so declining one is fine; the next one will find the engine idle.
     */
    suspend fun releaseEngine(): Boolean = withContext(inferenceDispatcher) {
        if (inFlight.get() > 0) {
            dbg("release skipped — ${inFlight.get()} generation(s) in flight")
            return@withContext false
        }
        val current = engine ?: return@withContext false
        engine = null
        runCatching { current.close() }
            .onFailure { dbg("engine close failed: ${it.message}") }
        dbg("engine released")
        true
    }

    /** Build the engine and run one throwaway generation so the first real
     *  question doesn't eat the ~10 s cold XNNPACK/prefill cost. */
    suspend fun warmUp() = withContext(inferenceDispatcher) {
        if (engine != null) return@withContext
        runCatching {
            val t0 = System.currentTimeMillis()
            // The warm-up's whole job is to pay the engine build + XNNPACK
            // init + first prefill once, in the background. What it *says*
            // is irrelevant, so ask for the shortest possible completion:
            // an open instruction like "Di listo." had the model produce ~40
            // words of English filler and burn ~3 s of the warm-up on tokens
            // nobody reads.
            generateResponse(WARM_UP_PROMPT)
            dbg("warm-up done (${System.currentTimeMillis() - t0} ms)")
        }
        Unit
    }

    /** Generated text, or null if the model isn't usable / timed out / failed. */
    suspend fun generateResponse(prompt: String): String? = withContext(inferenceDispatcher) {
        val llm = obtainEngine() ?: return@withContext null
        inFlight.incrementAndGet()
        try {
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
        } finally {
            inFlight.decrementAndGet()
        }
    }

    /**
     * Streams the model's answer, one partial-result batch at a time. Each
     * emission is the cumulative text so far; the flow completes when
     * generation finishes or [Constants.LOCAL_LLM_TIMEOUT_MS] elapses,
     * whichever comes first. Emits nothing and completes if the engine can't
     * be built, so the caller can fall back to "modo básico".
     *
     * MediaPipe invokes the [ProgressListener] on its own worker thread;
     * [callbackFlow] hands each batch back to the collector, and the producer
     * runs on [inferenceDispatcher] via [flowOn].
     */
    fun generateResponseStream(prompt: String): Flow<String> = callbackFlow {
        val llm = obtainEngine()
        if (llm == null) {
            dbg("stream: engine unavailable")
            close()
            return@callbackFlow
        }

        val safe = prompt
            .replace(Regex("[\\p{Cntrl}&&[^\n]]"), " ")
            .take(MAX_PROMPT_CHARS)

        val session = runCatching {
            LlmInferenceSession.createFromOptions(
                llm,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(TOP_K)
                    .setTemperature(TEMPERATURE)
                    .build(),
            )
        }.getOrElse {
            dbg("stream: session create failed: ${it.message}")
            close(it)
            return@callbackFlow
        }

        dbg("stream PROMPT (${safe.length} chars) >>>\n$safe")
        // AtomicReference, not StringBuilder: the listener runs on MediaPipe's
        // thread and the watchdog reads the accumulator from another.
        val acc = java.util.concurrent.atomic.AtomicReference("")
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val t0 = System.currentTimeMillis()

        // The final value (whole answer, mojibake repaired) must reach the
        // collector, so it goes out with a suspending send() before close()
        // rather than a trySend() that a full buffer could drop. Guarded so
        // "done" and the watchdog can't both run it.
        fun finish(reason: String) {
            if (!finished.compareAndSet(false, true)) return
            launch {
                val text = acc.get()
                dbg("stream $reason (${System.currentTimeMillis() - t0} ms) <<< \"$text\"")
                runCatching { if (text.isNotEmpty()) send(text.fixMojibake() ?: text) }
                close()
            }
        }

        val listener = ProgressListener<String> { partial, done ->
            if (!partial.isNullOrEmpty()) {
                val text = acc.updateAndGet { it + partial }
                trySend(text.fixMojibake() ?: text)
            }
            if (done) finish("RESPONSE")
        }

        var future: Future<*>? = null
        inFlight.incrementAndGet()
        try {
            session.addQueryChunk(safe)
            future = session.generateResponseAsync(listener)
        } catch (t: Throwable) {
            Log.w(TAG, "generateResponseAsync failed", t)
            dbg("stream: generateResponseAsync threw: ${t.stackTraceToString()}")
            close(t)
        }

        val watchdog = launch {
            delay(Constants.LOCAL_LLM_TIMEOUT_MS)
            finish("TIMEOUT")
        }

        awaitClose {
            watchdog.cancel()
            runCatching { future?.cancel(true) }
            runCatching { session.close() }
            inFlight.decrementAndGet()
        }
    }
        // Never drop a batch under back-pressure: emissions are cumulative, so
        // the last one carries the whole answer and must reach the collector
        // even if it is briefly slower than generation.
        .buffer(Channel.UNLIMITED)
        .flowOn(inferenceDispatcher)

    /**
     * MediaPipe `tasks-genai` mis-decodes some of the model's UTF-8 output as
     * Latin-1, so "kilómetros" can arrive as "kilÃ³metros". Crucially the
     * corruption is *partial* — a single response mixes "vehículo" (intact)
     * with "estÃ©" (doubly-encoded) — so a whole-string ISO-8859-1→UTF-8
     * round-trip fixes the broken accents but shreds the intact ones.
     *
     * Instead, rewrite only the mojibake pattern: a `Ã`/`Â` lead byte (which
     * never occurs in Spanish text) followed by a U+0080–U+00BF continuation
     * byte, decoded back to the character those two bytes are UTF-8 for.
     * Untouched accents are left alone.
     */
    private fun String?.fixMojibake(): String? {
        val s = this ?: return null
        if (!s.contains('Ã') && !s.contains('Â')) return s
        return MOJIBAKE_PAIR.replace(s) { m ->
            val bytes = byteArrayOf(m.value[0].code.toByte(), m.value[1].code.toByte())
            runCatching { String(bytes, Charsets.UTF_8) }.getOrDefault(m.value)
        }
    }

    private companion object {
        /** Deliberately trivial and self-terminating — see [warmUp]. */
        const val WARM_UP_PROMPT = "Responde solo con la palabra: ok"

        const val MAX_TOKENS = 512
        const val TOP_K = 40
        const val TEMPERATURE = 0.2f

        /** UTF-8-as-Latin-1 mojibake: `Ã`/`Â` then a continuation byte. */
        val MOJIBAKE_PAIR = Regex("[ÂÃ][-¿]")

        /** Hard ceiling on prompt length fed to the native engine. Well under
         *  the model's KV window; PromptBuilder already caps to a similar size. */
        const val MAX_PROMPT_CHARS = 1400
        const val TAG = "LocalLlmManager"
    }
}
