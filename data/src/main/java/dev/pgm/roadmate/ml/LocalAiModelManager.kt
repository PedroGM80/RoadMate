package dev.pgm.roadmate.ml

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.data.BuildConfig
import dev.pgm.roadmate.domain.model.LocalAiStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Gets the universal local-AI model onto the device with **no account, no
 * token, no manual steps**: a plain HTTPS download (resumable) of a small
 * Apache-2.0 model to internal storage, run afterwards through MediaPipe by
 * [LocalLlmManager].
 *
 * The download is **unmetered-network only** — if the active network is
 * metered it parks at [LocalAiStatus.WaitingForWifi] and auto-starts the
 * moment an unmetered network appears. [RoadMateViewModel] kicks
 * [fetch] automatically once it sees [LocalAiStatus.ModelDownloadable]; the
 * UI also exposes a manual button for ret/resume.
 *
 * URL / filename / expected size come from `BuildConfig` (overridable in
 * `local.properties`). A blank URL disables the path → [LocalAiStatus.Unavailable].
 */
@Singleton
class LocalAiModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS) // a multi-hundred-MB download can take a while
            .build()
    }

    private val modelDir = File(context.filesDir, "models")
    private val modelFile = File(modelDir, BuildConfig.LOCAL_AI_MODEL_FILENAME)
    private val partFile = File(modelDir, BuildConfig.LOCAL_AI_MODEL_FILENAME + ".part")
    private val expectedSize = BuildConfig.LOCAL_AI_MODEL_SIZE_BYTES

    private val _status = MutableStateFlow<LocalAiStatus>(LocalAiStatus.Checking)
    val status: StateFlow<LocalAiStatus> = _status.asStateFlow()

    @Volatile
    private var downloadJob: Job? = null

    @Volatile
    private var awaitingUnmetered = false

    /**
     * The registered "tell me when Wi-Fi appears" callback, kept so it can be
     * taken down again. Without the reference the app went on listening for a
     * network it no longer needed — after the model finished by another route,
     * or after the download path was disabled.
     */
    @Volatile
    private var unmeteredCallback: ConnectivityManager.NetworkCallback? = null

    /** Cheap re-evaluation of resting state; safe to call repeatedly. */
    fun refreshStatus() {
        _status.value = when {
            BuildConfig.LOCAL_AI_MODEL_URL.isBlank() -> LocalAiStatus.Unavailable
            isModelComplete() -> LocalAiStatus.ReadyLocalModel
            downloadJob?.isActive == true -> _status.value
            awaitingUnmetered -> LocalAiStatus.WaitingForWifi
            else -> LocalAiStatus.ModelDownloadable
        }
    }

    /** The model file, or null until a verified copy is on disk. */
    fun modelFile(): File? = modelFile.takeIf { isModelComplete() }

    private fun isModelComplete(): Boolean =
        modelFile.isFile &&
            modelFile.length() >= MIN_PLAUSIBLE_BYTES &&
            (expectedSize <= 0L || modelFile.length() == expectedSize)

    /**
     * Starts (or resumes) the download. No-op if already complete or in
     * flight. Defers to Wi-Fi when the network is metered.
     */
    fun fetch() {
        if (BuildConfig.LOCAL_AI_MODEL_URL.isBlank()) {
            stopWaitingForUnmetered()
            _status.value = LocalAiStatus.Unavailable
            return
        }
        if (isModelComplete()) {
            stopWaitingForUnmetered()
            _status.value = LocalAiStatus.ReadyLocalModel
            return
        }
        if (downloadJob?.isActive == true) return

        if (isMeteredOrOffline()) {
            _status.value = LocalAiStatus.WaitingForWifi
            awaitUnmeteredThenFetch()
            return
        }

        downloadJob = scope.launch { runDownload() }
    }

    private suspend fun runDownload() {
        _status.value = LocalAiStatus.Downloading(0f)
        try {
            modelDir.mkdirs()
            val resumeFrom = if (partFile.isFile) partFile.length() else 0L

            // The whole file is already on disk in .part (killed between the
            // last write and the rename) — just finalize it, no request.
            if (expectedSize > 0L && resumeFrom >= expectedSize) {
                finalizePartFile()
                return
            }

            val request = Request.Builder()
                .url(BuildConfig.LOCAL_AI_MODEL_URL)
                .apply { if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-") }
                .build()

            client.newCall(request).execute().use { response ->
                // Server rejected the resume offset — drop the stale .part so
                // the next fetch() restarts cleanly.
                if (response.code == 416) {
                    partFile.delete()
                    _status.value = LocalAiStatus.DownloadFailed("reintenta la descarga")
                    return
                }
                val partial = response.code == 206
                if (!response.isSuccessful) {
                    _status.value = LocalAiStatus.DownloadFailed("HTTP ${response.code}")
                    return
                }
                val body = response.body
                val appendFrom = if (partial) resumeFrom else 0L
                if (!partial && partFile.exists()) partFile.delete()

                val bodyLength = body.contentLength()
                val totalBytes = when {
                    bodyLength > 0L -> appendFrom + bodyLength
                    expectedSize > 0L -> expectedSize
                    else -> -1L
                }

                body.byteStream().use { input ->
                    FileOutputStream(partFile, /* append = */ partial).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = appendFrom
                        var lastEmitted = -1
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0L) {
                                val pct = ((downloaded * 100L) / totalBytes).toInt()
                                if (pct != lastEmitted) {
                                    lastEmitted = pct
                                    _status.value = LocalAiStatus.Downloading(
                                        (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }
            }

            finalizePartFile()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "model download failed", e)
            // Keep the .part file so the next fetch() resumes instead of restarting.
            _status.value = LocalAiStatus.DownloadFailed(e.message ?: "error de red")
        }
    }

    /** Validates the fully-downloaded `.part` file and atomically promotes it. */
    private fun finalizePartFile() {
        if (expectedSize > 0L && partFile.length() != expectedSize) {
            partFile.delete()
            _status.value = LocalAiStatus.DownloadFailed("tamaño inesperado, reintenta")
            return
        }
        if (partFile.length() < MIN_PLAUSIBLE_BYTES) {
            partFile.delete()
            _status.value = LocalAiStatus.DownloadFailed("descarga incompleta")
            return
        }
        if (modelFile.exists()) modelFile.delete()
        if (!partFile.renameTo(modelFile)) {
            _status.value = LocalAiStatus.DownloadFailed("no se pudo guardar el modelo")
            return
        }
        stopWaitingForUnmetered()
        _status.value = LocalAiStatus.ReadyLocalModel
    }

    private fun isMeteredOrOffline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun awaitUnmeteredThenFetch() {
        if (awaitingUnmetered) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                stopWaitingForUnmetered()
                fetch()
            }
        }
        awaitingUnmetered = true
        unmeteredCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onFailure {
                awaitingUnmetered = false
                unmeteredCallback = null
                Log.w(TAG, "could not watch for Wi-Fi", it)
            }
    }

    private fun stopWaitingForUnmetered() {
        val callback = unmeteredCallback ?: run { awaitingUnmetered = false; return }
        unmeteredCallback = null
        awaitingUnmetered = false
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    private companion object {
        const val MIN_PLAUSIBLE_BYTES = 50_000_000L
        const val TAG = "LocalAiModelManager"
    }
}
