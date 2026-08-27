package dev.pgm.roadmate.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository
import dev.pgm.roadmate.ml.SpeechRecognitionManager
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Bridges [SpeechRecognitionManager]'s callback API into a single suspend call.
 * A fresh manager/recognizer is created per call, matching SpeechRecognizer's
 * one-shot-per-session usage model.
 */
class SpeechRecognitionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechRecognitionRepository {

    override suspend fun recognizeSpeech(): String = suspendCancellableCoroutine { continuation ->
        lateinit var manager: SpeechRecognitionManager
        manager = SpeechRecognitionManager(
            context = context,
            onResult = { text ->
                manager.destroy()
                if (continuation.isActive) continuation.resume(text)
            },
            onError = {
                manager.destroy()
                if (continuation.isActive) continuation.resume("")
            }
        )
        continuation.invokeOnCancellation { manager.destroy() }
        manager.startListening()
    }
}
