package dev.pgm.roadmate.ml

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Plays generated responses as speech using a single shared TTS engine instance.
 * Utterances requested before the engine finishes initializing are queued and
 * flushed once it becomes ready.
 *
 * [isSpeaking] lets the recogniser hold off opening the mic while RoadMate is
 * still talking — otherwise it transcribes its own voice and answers it.
 *
 * Two things here exist for the car specifically:
 *
 * - **Audio focus.** Nothing is spoken without holding it ([SpeechAudioFocus]).
 *   A head unit routes by focus, so an utterance sent without it is silently
 *   dropped — the app looks like it is speaking, the state flow says so, and
 *   the car says nothing.
 * - **Nothing here can hang the caller.** `awaitDoneSpeaking()` is on the golden
 *   path of every question: the mic doesn't open until speech finishes. So
 *   every way this class can fail — an engine that never initialises, one that
 *   dies mid-sentence, a `speak()` that returns ERROR, a `onDone` that never
 *   arrives — ends in the callback being run and [isSpeaking] going false. A
 *   silent RoadMate is a bug; a frozen one is a worse bug.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext context: Context,
    private val audioFocus: SpeechAudioFocus,
) {

    // Touched from three threads: the caller (main), the TTS init callback and
    // the UtteranceProgressListener (both binder threads). The plain
    // list/map/flag here could drop a done-callback or throw a
    // ConcurrentModificationException while flushing the queue.
    @Volatile
    private var isReady = false

    /** Set when the engine reports failure, or never comes back at all. */
    @Volatile
    private var isDead = false

    private val pendingUtterances: MutableList<Pair<String, () -> Unit>> =
        Collections.synchronizedList(mutableListOf())
    private val doneCallbacks = ConcurrentHashMap<String, () -> Unit>()
    private val watchdogs = ConcurrentHashMap<String, Job>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /** Speech rate to apply; kept so a call before the engine is ready still sticks. */
    @Volatile
    private var speechRate = 1.0f

    private val engine: TextToSpeech? = runCatching {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                setupEngine()
                flushPending()
            } else {
                DebugTrace.log("TTS init failed (status=$status)")
                fail()
            }
        }
    }.onFailure { DebugTrace.log("TTS ctor threw: ${it.message}") }.getOrNull()

    init {
        if (engine == null) {
            fail()
        } else {
            // No TTS engine installed at all leaves the init callback
            // un-invoked forever, and with it every awaitDoneSpeaking() in the
            // app. Give it a bounded wait and then treat it as dead.
            scope.launch {
                delay(INIT_TIMEOUT_MS.milliseconds)
                if (!isReady && !isDead) {
                    DebugTrace.log("TTS init timed out after $INIT_TIMEOUT_MS ms")
                    fail()
                }
            }
        }
    }

    private fun setupEngine() {
        val engine = engine ?: return
        runCatching {
            // RoadMate speaks Spanish and nothing else — every string it utters is
            // Spanish. Following the device locale made an English-locale phone
            // read Spanish text with an English voice. Fall back to the device
            // locale only if no Spanish voice is installed.
            val spanish = engine.setLanguage(SPANISH)
            if (spanish == TextToSpeech.LANG_MISSING_DATA || spanish == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.getDefault()
            }
            // Navigation-guidance usage, matching the focus request — see
            // SpeechAudioFocus for why USAGE_ASSISTANT left the car silent.
            engine.setAudioAttributes(SpeechAudioFocus.ATTRIBUTES)
            engine.setSpeechRate(speechRate)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = finish(utteranceId, invokeCallback = true)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finish(utteranceId, invokeCallback = true)

                override fun onError(utteranceId: String?, errorCode: Int) {
                    DebugTrace.log("TTS utterance error $errorCode")
                    finish(utteranceId, invokeCallback = true)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) =
                    finish(utteranceId, invokeCallback = true)
            })
        }.onFailure { DebugTrace.log("TTS setup threw: ${it.message}") }
    }

    private fun flushPending() {
        val queued = drainPending()
        queued.forEach { (text, onDone) -> enqueue(text, onDone) }
    }

    private fun drainPending(): List<Pair<String, () -> Unit>> =
        synchronized(pendingUtterances) {
            pendingUtterances.toList().also { pendingUtterances.clear() }
        }

    /**
     * Gives up on the engine for good: everything queued gets its callback so
     * no caller is left waiting, and the app carries on mute rather than stuck.
     */
    private fun fail() {
        isDead = true
        isReady = false
        val queued = drainPending()
        val pendingCallbacks = doneCallbacks.values.toList()
        doneCallbacks.clear()
        cancelWatchdogs()
        _isSpeaking.value = false
        audioFocus.abandon()
        queued.forEach { (_, onDone) -> runCatching { onDone() } }
        pendingCallbacks.forEach { runCatching { it() } }
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        // TEMP instrumentation (strip with the rest of DebugTrace): timestamps
        // each utterance so the streaming-answer latency can be read off the
        // trace — gap from "LLM stream PROMPT" to the first "TTS speak".
        DebugTrace.log("TTS speak ${if (isReady) "" else "(queued) "}\"${text.take(90)}\"")
        if (text.isBlank() || isDead) {
            runCatching { onDone() }
            return
        }
        if (!isReady) {
            pendingUtterances.add(text to onDone)
            _isSpeaking.value = true
            return
        }
        enqueue(text, onDone)
    }

    fun stop() {
        engine?.let { runCatching { it.stop() } }
        val queued = drainPending()
        val pendingCallbacks = doneCallbacks.values.toList()
        doneCallbacks.clear()
        cancelWatchdogs()
        _isSpeaking.value = false
        audioFocus.abandon()
        // A stopped utterance still has to release whoever was waiting on it —
        // stop() is exactly what runs before the mic opens.
        queued.forEach { (_, onDone) -> runCatching { onDone() } }
        pendingCallbacks.forEach { runCatching { it() } }
    }

    /** Sticky on the engine; a value set before init is applied in [setupEngine]. */
    fun setRate(rate: Float) {
        speechRate = rate.coerceIn(MIN_RATE, MAX_RATE)
        if (isReady) engine?.let { runCatching { it.setSpeechRate(speechRate) } }
    }

    private fun enqueue(text: String, onDone: () -> Unit) {
        val engine = engine ?: run { runCatching { onDone() }; return }
        val utteranceId = UUID.randomUUID().toString()
        doneCallbacks[utteranceId] = onDone
        _isSpeaking.value = true
        // Focus first: the host decides where this stream goes at speak() time,
        // and without it the sentence is dropped before it reaches a speaker.
        audioFocus.request()
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)
        if (result == TextToSpeech.ERROR) {
            DebugTrace.log("TTS speak returned ERROR")
            finish(utteranceId, invokeCallback = true)
            return
        }
        // The engine can die between accepting an utterance and reporting it
        // done — a process kill, a bad voice, a host that never plays it. The
        // watchdog is what stops that from freezing the question pipeline.
        watchdogs[utteranceId] = scope.launch {
            delay(utteranceTimeoutMs(text).milliseconds)
            if (doneCallbacks.containsKey(utteranceId)) {
                DebugTrace.log("TTS utterance watchdog fired")
                finish(utteranceId, invokeCallback = true)
            }
        }
    }

    /** Retires one utterance and, if it was the last, drops audio focus. */
    private fun finish(utteranceId: String?, invokeCallback: Boolean) {
        val callback = utteranceId?.let { doneCallbacks.remove(it) }
        utteranceId?.let { watchdogs.remove(it)?.cancel() }
        if (invokeCallback && callback != null) runCatching { callback() }
        refreshSpeakingState()
    }

    private fun cancelWatchdogs() {
        watchdogs.values.forEach { it.cancel() }
        watchdogs.clear()
    }

    @Synchronized
    private fun refreshSpeakingState() {
        val speaking = doneCallbacks.isNotEmpty() || pendingUtterances.isNotEmpty()
        _isSpeaking.value = speaking
        if (!speaking) audioFocus.abandon()
    }

    /**
     * A long answer read at a slow rate can legitimately take a while; the
     * timeout scales with it so the watchdog never cuts real speech short.
     */
    private fun utteranceTimeoutMs(text: String): Long {
        val perChar = (CHAR_MS / speechRate.coerceAtLeast(MIN_RATE)).toLong()
        return (MIN_UTTERANCE_TIMEOUT_MS + text.length * perChar)
            .coerceAtMost(MAX_UTTERANCE_TIMEOUT_MS)
    }

    private companion object {
        val SPANISH: Locale = Locale.forLanguageTag("es-ES")

        /** Long enough for a cold TTS service to come up, short enough to not strand a question. */
        const val INIT_TIMEOUT_MS = 12_000L

        const val MIN_UTTERANCE_TIMEOUT_MS = 15_000L
        const val MAX_UTTERANCE_TIMEOUT_MS = 180_000L

        /** Rough upper bound on how long one character takes to read at rate 1.0. */
        const val CHAR_MS = 140f

        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
    }
}
