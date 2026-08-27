package dev.pgm.roadmate.domain.repository

/**
 * Contract for speaking text out loud.
 */
interface SpeechSynthesisRepository {

    fun speak(text: String, onDone: () -> Unit = {})

    fun stop()
}
