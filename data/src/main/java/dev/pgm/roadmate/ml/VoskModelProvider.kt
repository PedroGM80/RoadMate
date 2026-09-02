package dev.pgm.roadmate.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled Vosk Spanish model once and hands the same [Model] to
 * every consumer — [VoskSpeechRecognizer] for dictation and
 * [WakeWordDetector] for "RoadMate" spotting. One model in memory, not one
 * per feature.
 *
 * The ~39 MB model ships zipped in the app's assets
 * (`vosk-model-small-es-0.42.zip`, fetched at build time) and unpacks to
 * internal storage on first use.
 */
@Singleton
class VoskModelProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val mutex = Mutex()

    @Volatile
    private var model: Model? = null

    /** The shared model, or null if it can't be unpacked/loaded. */
    suspend fun get(): Model? {
        model?.let { return it }
        return mutex.withLock {
            model ?: withContext(Dispatchers.IO) {
                runCatching {
                    Model(ensureModelUnpacked().absolutePath)
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

    private companion object {
        const val MODEL_DIR = "vosk-es"
        const val ASSET_ZIP = "vosk-model-small-es-0.42.zip"
        const val TAG = "VoskModelProvider"
    }
}
