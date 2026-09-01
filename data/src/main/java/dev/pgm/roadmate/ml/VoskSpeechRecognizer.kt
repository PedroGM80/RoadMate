package dev.pgm.roadmate.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.model.SpeechRecognitionEvent
import dev.pgm.roadmate.utils.SpokenText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully offline Spanish speech-to-text via Vosk (Kaldi). Unlike Android's
 * `SpeechRecognizer` + `EXTRA_PREFER_OFFLINE`, this does **not** depend on a
 * Google speech pack being installed — the ~39 MB model is bundled in the
 * app's assets (`vosk-model-small-es-0.42.zip`, fetched at build time) and
 * unpacked to internal storage on first use.
 *
 * [recognize] streams live [SpeechRecognitionEvent.Partial]s while the user
 * speaks, then one [SpeechRecognitionEvent.Result] (at the first pause) or
 * [SpeechRecognitionEvent.Failed].
 */
@Singleton
class VoskSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val carMicrophonePreference = CarMicrophonePreference(context)
    private val modelMutex = Mutex()

    @Volatile
    private var model: Model? = null

    init {
        // Warm the model so the first mic tap isn't a 1–2 s cold load.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { loadModel() } }
    }

    fun recognize(): Flow<SpeechRecognitionEvent> = callbackFlow {
        val loadedModel = runCatching { loadModel() }.getOrNull()
        if (loadedModel == null) {
            trySend(SpeechRecognitionEvent.Failed(SpokenText.SPEECH_NOT_READY))
            close()
            return@callbackFlow
        }

        carMicrophonePreference.preferCarMicrophoneIfAvailable()

        val recognizer = Recognizer(loadedModel, SAMPLE_RATE)
        val listener = object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                val text = hypothesis.field("partial")
                if (text.isNotBlank()) trySend(SpeechRecognitionEvent.Partial(text))
            }

            override fun onResult(hypothesis: String?) {
                trySend(SpeechRecognitionEvent.Result(hypothesis.field("text")))
                close()
            }

            override fun onFinalResult(hypothesis: String?) {
                val text = hypothesis.field("text")
                if (trySend(SpeechRecognitionEvent.Result(text)).isSuccess) close()
            }

            override fun onError(exception: Exception?) {
                Log.w(TAG, "recognition error", exception)
                trySend(SpeechRecognitionEvent.Failed(SpokenText.SPEECH_FLOW_ERROR))
                close()
            }

            override fun onTimeout() {
                trySend(SpeechRecognitionEvent.Result(""))
                close()
            }
        }

        val speechService = try {
            SpeechService(recognizer, SAMPLE_RATE)
        } catch (e: IOException) {
            Log.w(TAG, "could not open microphone", e)
            trySend(SpeechRecognitionEvent.Failed(SpokenText.SPEECH_MIC_DENIED))
            recognizer.close()
            carMicrophonePreference.clearPreference()
            close()
            return@callbackFlow
        }

        speechService.startListening(listener, MAX_UTTERANCE_MS)

        awaitClose {
            runCatching { speechService.stop() }
            runCatching { speechService.shutdown() }
            runCatching { recognizer.close() }
            carMicrophonePreference.clearPreference()
        }
    }

    private suspend fun loadModel(): Model? {
        model?.let { return it }
        return modelMutex.withLock {
            model ?: withContext(Dispatchers.IO) {
                runCatching {
                    val root = ensureModelUnpacked()
                    Model(root.absolutePath)
                }.onFailure { Log.e(TAG, "Vosk model load failed", it) }.getOrNull()
            }?.also { model = it }
        }
    }

    /** Unzips the bundled model on first run; returns the dir that holds `conf/`. */
    private fun ensureModelUnpacked(): File {
        val target = File(context.filesDir, MODEL_DIR)
        val marker = File(target, ".unpacked")
        if (!marker.exists()) {
            target.deleteRecursively()
            target.mkdirs()
            context.assets.open(ASSET_ZIP).use { raw ->
                ZipInputStream(raw).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val outFile = File(target, entry.name)
                        if (!outFile.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                            throw IOException("Zip entry escapes target dir: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            marker.createNewFile()
        }
        // The archive nests everything under a single "vosk-model-small-es-0.42/" dir.
        return if (File(target, "conf").isDirectory) {
            target
        } else {
            target.listFiles()?.firstOrNull { File(it, "conf").isDirectory }
                ?: throw IOException("Unpacked Vosk model has no conf/ dir")
        }
    }

    private fun String?.field(name: String): String =
        if (this.isNullOrBlank()) "" else runCatching { JSONObject(this).optString(name).trim() }.getOrDefault("")

    private companion object {
        const val SAMPLE_RATE = 16000.0f
        const val MAX_UTTERANCE_MS = 12_000
        const val MODEL_DIR = "vosk-es"
        const val ASSET_ZIP = "vosk-model-small-es-0.42.zip"
        const val TAG = "VoskSpeechRecognizer"
    }
}
