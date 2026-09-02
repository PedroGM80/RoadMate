package dev.pgm.roadmate.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for speaking text out loud.
 */
interface SpeechSynthesisRepository {

    /** True from the moment [speak] is called until the last utterance finishes (or [stop]). */
    val isSpeaking: StateFlow<Boolean>

    fun speak(text: String, onDone: () -> Unit = {})

    fun stop()

    /** Sets how fast speech is read: 1.0 is normal, lower is slower. */
    fun setSpeechRate(rate: Float) {}

    /** Suspends until nothing is being spoken. Returns at once if already quiet. */
    suspend fun awaitDoneSpeaking()
}
