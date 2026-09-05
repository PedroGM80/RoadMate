package dev.pgm.roadmate.ml

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio focus for everything RoadMate says out loud.
 *
 * On the phone a TTS utterance is audible without asking anyone: the stream is
 * mixed in and that is that. In a car it is not. The head unit routes one
 * source to the speakers at a time and decides which by audio focus — an app
 * that speaks without holding focus is either ducked to nothing or dropped
 * entirely, silently, with no error anywhere. That is why RoadMate was mute in
 * Android Auto while the same build talked fine on the phone.
 *
 * [ATTRIBUTES] is the other half of it. `USAGE_ASSISTANT` describes a
 * conversational assistant and several hosts have no route for it at all;
 * `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` is the one every head unit is
 * required to carry, because it is what turn instructions come through. It
 * also gets the behaviour a driver expects: music ducks under the sentence
 * instead of stopping.
 *
 * Focus is transient-may-duck, not gain: RoadMate says a sentence and gets out
 * of the way. Holding permanent focus would stop the driver's music dead every
 * time it opened its mouth.
 */
@Singleton
class SpeechAudioFocus @Inject constructor(@ApplicationContext context: Context) {

    private val audioManager: AudioManager? =
        runCatching { context.getSystemService(AudioManager::class.java) }.getOrNull()

    /** Guards [request]/[abandon] — called from the caller thread and TTS binder threads. */
    private val lock = Any()

    private var held = false

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ATTRIBUTES)
            // RoadMate never pauses for a duck — its utterances are seconds
            // long and the host lowers the other stream, not this one.
            .setWillPauseWhenDucked(false)
            // Nothing to resume: an interrupted sentence is dropped, not
            // queued. The listener exists so the host has one to talk to.
            .setOnAudioFocusChangeListener { }
            .build()

    /**
     * Takes focus if it isn't already held. Returns false only when the system
     * refuses (a call in progress, another app holding exclusive focus) — the
     * caller still speaks: a refused request on a phone with no head unit is
     * routine, and a sentence nobody hears beats no sentence at all.
     */
    fun request(): Boolean = synchronized(lock) {
        if (held) return true
        val manager = audioManager ?: return false
        val granted = runCatching { manager.requestAudioFocus(focusRequest) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        held = granted
        if (!granted) DebugTrace.log("audio focus refused")
        granted
    }

    /** Releases focus so the driver's music comes back up. Safe to call twice. */
    fun abandon() = synchronized(lock) {
        if (held) {
            held = false
            audioManager?.let { runCatching { it.abandonAudioFocusRequest(focusRequest) } }
        }
    }

    companion object {
        /**
         * Shared with the TTS engine itself — the engine's attributes and the
         * focus request have to describe the same stream or the host ducks one
         * thing and plays another.
         */
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
