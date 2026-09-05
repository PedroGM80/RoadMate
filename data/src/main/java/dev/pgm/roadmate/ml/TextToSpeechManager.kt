package dev.pgm.roadmate.ml

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays generated responses as speech using a single shared TTS engine instance.
 * Utterances requested before the engine finishes initializing are queued and
 * flushed once it becomes ready.
 *
 * [isSpeaking] lets the recogniser hold off opening the mic while RoadMate is
 * still talking — otherwise it transcribes its own voice and answers it.
 */
@Singleton
class TextToSpeechManager @Inject constructor(@ApplicationContext context: Context) {

    // Android Auto only routes the phone's audio to the car speakers, and only
    // ducks the car's music, while the app holds audio focus. Without a focus
    // request the answer plays silently on the projected head unit even though
    // the same utterance is audible on the handset. USAGE_ASSISTANCE_NAVIGATION_
    // GUIDANCE is the channel a navigation-category car app is expected to speak
    // on; TRANSIENT_MAY_DUCK lowers music instead of pausing it for the few
    // seconds an answer takes.
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val speechAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAudioAttributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change ->
                // A call or the car's own assistant taking the channel: drop
                // the rest of the answer rather than talk under it.
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    stop()
                }
            }
            .build()

    /** Whether a focus request is currently outstanding, so it is abandoned once. */
    @Volatile
    private var holdingFocus = false

    // Touched from three threads: the caller (main), the TTS init callback and
    // the UtteranceProgressListener (both binder threads). The plain
    // list/map/flag here could drop a done-callback or throw a
    // ConcurrentModificationException while flushing the queue.
    @Volatile
    private var isReady = false
    private val pendingUtterances: MutableList<Pair<String, () -> Unit>> =
        Collections.synchronizedList(mutableListOf())
    private val doneCallbacks = ConcurrentHashMap<String, () -> Unit>()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /** Speech rate to apply; kept so a call before the engine is ready still sticks. */
    @Volatile
    private var speechRate = 1.0f

    private val engine: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            setupEngine()
            flushPending()
        }
    }

    private fun setupEngine() {
        // RoadMate speaks Spanish and nothing else — every string it utters is
        // Spanish. Following the device locale made an English-locale phone
        // read Spanish text with an English voice. Fall back to the device
        // locale only if no Spanish voice is installed.
        val spanish = engine.setLanguage(SPANISH)
        if (spanish == TextToSpeech.LANG_MISSING_DATA || spanish == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.getDefault()
        }
        engine.setAudioAttributes(speechAudioAttributes)
        engine.setSpeechRate(speechRate)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { doneCallbacks.remove(it)?.invoke() }
                refreshSpeakingState()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { doneCallbacks.remove(it) }
                refreshSpeakingState()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { doneCallbacks.remove(it) }
                refreshSpeakingState()
            }
        })
    }

    private fun flushPending() {
        val queued = synchronized(pendingUtterances) {
            pendingUtterances.toList().also { pendingUtterances.clear() }
        }
        queued.forEach { (text, onDone) -> enqueue(text, onDone) }
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        // TEMP instrumentation (strip with the rest of DebugTrace): timestamps
        // each utterance so the streaming-answer latency can be read off the
        // trace — gap from "LLM stream PROMPT" to the first "TTS speak".
        DebugTrace.log("TTS speak ${if (isReady) "" else "(queued) "}\"${text.take(90)}\"")
        if (text.isBlank()) {
            onDone()
            return
        }
        if (!isReady) {
            pendingUtterances.add(text to onDone)
            acquireFocus()
            _isSpeaking.value = true
            return
        }
        enqueue(text, onDone)
    }

    fun stop() {
        engine.stop()
        pendingUtterances.clear()
        doneCallbacks.clear()
        releaseFocus()
        _isSpeaking.value = false
    }

    /** Sticky on the engine; a value set before init is applied in [setupEngine]. */
    fun setRate(rate: Float) {
        speechRate = rate
        if (isReady) engine.setSpeechRate(rate)
    }

    private fun enqueue(text: String, onDone: () -> Unit) {
        val utteranceId = UUID.randomUUID().toString()
        doneCallbacks[utteranceId] = onDone
        acquireFocus()
        _isSpeaking.value = true
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    @Synchronized
    private fun refreshSpeakingState() {
        val speaking = doneCallbacks.isNotEmpty() || pendingUtterances.isNotEmpty()
        if (!speaking) releaseFocus()
        _isSpeaking.value = speaking
    }

    @Synchronized
    private fun acquireFocus() {
        if (holdingFocus) return
        holdingFocus = audioManager?.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Synchronized
    private fun releaseFocus() {
        if (!holdingFocus) return
        holdingFocus = false
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }

    private companion object {
        val SPANISH: Locale = Locale.forLanguageTag("es-ES")
    }
}
