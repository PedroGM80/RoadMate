package dev.pgm.roadmate.data.repository

import android.os.SystemClock
import android.util.Log
import dev.pgm.roadmate.domain.audio.PcmAudioSource
import dev.pgm.roadmate.domain.repository.PcmTranscriber
import dev.pgm.roadmate.ml.VoskModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Recognizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds a caller-supplied PCM stream straight into a Vosk [Recognizer],
 * instead of letting Vosk's own `SpeechService` open an `AudioRecord`. Same
 * model, same language, same offline guarantee as
 * [dev.pgm.roadmate.ml.VoskSpeechRecognizer] — the only difference is who
 * owns the microphone.
 */
@Singleton
class VoskPcmTranscriberImpl @Inject constructor(
    private val modelProvider: VoskModelProvider,
) : PcmTranscriber {

    override suspend fun transcribe(source: PcmAudioSource): String = withContext(Dispatchers.IO) {
        val model = modelProvider.get()
        if (model == null) {
            Log.w(TAG, "Vosk model unavailable")
            return@withContext ""
        }

        val recognizer = Recognizer(model, source.sampleRate.toFloat())
        val started = runCatching { source.start() }
        if (started.isFailure) {
            Log.w(TAG, "could not open audio source", started.exceptionOrNull())
            recognizer.close()
            return@withContext ""
        }

        try {
            val buffer = ByteArray(source.bufferSize)
            val deadline = SystemClock.elapsedRealtime() + MAX_UTTERANCE_MS
            var lastVoiceAt = SystemClock.elapsedRealtime()
            var heardAnything = false

            while (SystemClock.elapsedRealtime() < deadline && currentCoroutineContext().isActive) {
                val read = source.read(buffer)
                if (read < 0) break
                if (read == 0) continue

                if (recognizer.acceptWaveForm(buffer, read)) {
                    // Vosk says the utterance ended. Anything non-empty is the
                    // answer; an empty result is just a pause, so keep going.
                    val text = recognizer.result.jsonField("text")
                    if (text.isNotBlank()) return@withContext text
                } else if (recognizer.partialResult.jsonField("partial").isNotBlank()) {
                    heardAnything = true
                    lastVoiceAt = SystemClock.elapsedRealtime()
                }

                // Nothing at all after a few seconds means the driver never
                // spoke — bail out instead of holding the car's mic for the
                // full utterance budget.
                val quietFor = SystemClock.elapsedRealtime() - lastVoiceAt
                if (!heardAnything && quietFor > SILENCE_GIVE_UP_MS) break
                if (heardAnything && quietFor > END_OF_SPEECH_MS) break
            }

            recognizer.finalResult.jsonField("text")
        } catch (t: Throwable) {
            Log.w(TAG, "transcription failed", t)
            ""
        } finally {
            runCatching { source.stop() }
            runCatching { recognizer.close() }
        }
    }

    private fun String?.jsonField(name: String): String =
        if (isNullOrBlank()) "" else runCatching { JSONObject(this).optString(name).trim() }.getOrDefault("")

    private companion object {
        const val TAG = "VoskPcmTranscriber"

        /** Hard ceiling on one question, matching VoskSpeechRecognizer. */
        const val MAX_UTTERANCE_MS = 12_000L

        /** Give up early when the driver never started talking. */
        const val SILENCE_GIVE_UP_MS = 4_000L

        /** Trailing pause that ends an utterance once speech has been heard. */
        const val END_OF_SPEECH_MS = 1_200L
    }
}
