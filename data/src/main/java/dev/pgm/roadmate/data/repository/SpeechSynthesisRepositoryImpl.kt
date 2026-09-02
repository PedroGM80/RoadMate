package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.ml.TextToSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechSynthesisRepositoryImpl @Inject constructor(
    private val textToSpeechManager: TextToSpeechManager,
    private val preferences: AssistantPreferencesRepository,
) : SpeechSynthesisRepository {

    override val isSpeaking: StateFlow<Boolean> = textToSpeechManager.isSpeaking

    init {
        // Carry a remembered "más despacio" / "más rápido" across restarts.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            textToSpeechManager.setRate(preferences.speechRate.first())
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        textToSpeechManager.speak(text, onDone)
    }

    override fun stop() {
        textToSpeechManager.stop()
    }

    override fun setSpeechRate(rate: Float) {
        textToSpeechManager.setRate(rate)
    }

    override suspend fun awaitDoneSpeaking() {
        isSpeaking.first { !it }
    }
}
