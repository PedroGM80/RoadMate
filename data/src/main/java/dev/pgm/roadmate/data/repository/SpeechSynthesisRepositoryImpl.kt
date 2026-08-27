package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.ml.TextToSpeechManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechSynthesisRepositoryImpl @Inject constructor(
    private val textToSpeechManager: TextToSpeechManager
) : SpeechSynthesisRepository {

    override fun speak(text: String, onDone: () -> Unit) {
        textToSpeechManager.speak(text, onDone)
    }

    override fun stop() {
        textToSpeechManager.stop()
    }
}
