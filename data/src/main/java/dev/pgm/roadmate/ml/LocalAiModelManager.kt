package dev.pgm.roadmate.ml

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.model.LocalAiCatalog
import dev.pgm.roadmate.domain.model.LocalAiModel
import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * Gets the selected local-AI model onto the device with **no account, no
 * token, no manual steps**: a plain resumable HTTPS download of an
 * Apache-2.0 `.task` model to internal storage, run afterwards through
 * MediaPipe by [LocalLlmManager].
 *
 * Which model is a driver choice ([LocalAiCatalog], persisted via
 * [AssistantPreferencesRepository]); the app keeps **one** on disk and
 * switching deletes the previous file. On launch, if the persisted choice
 * isn't set, a completed model already on disk is adopted, else the
 * recommended one.
 *
 * The download is **unmetered-network only** — on a metered network it parks
 * at [LocalAiStatus.WaitingForWifi] and auto-starts when Wi-Fi appears.
 */
@Singleton
class LocalAiModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AssistantPreferencesRepository,
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

    @Volatile
    private var model: LocalAiModel = LocalAiCatalog.recommended

    private val modelFile get() = File(modelDir, model.fileName)
    private val partFile get() = File(modelDir, model.fileName + ".part")
    private val expectedSize get() = model.sizeBytes

    private val _status = MutableStateFlow<LocalAiStatus>(LocalAiStatus.Checking)
    val status: StateFlow<LocalAiStatus> = _status.asStateFlow()

    private val _selectedId = MutableStateFlow(model.id)
    /** Id of the model in use right now (persisted choice, on-disk adoption, or recommended). */
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    private val _modelChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when the active model file changes, so a loaded engine can be dropped. */
    val modelChanged: SharedFlow<Unit> = _modelChanged.asSharedFlow()

    @Volatile
    private var downloadJob: Job? = null

    @Volatile
    private var awaitingUnmetered = false

    @Volatile
    private var unmeteredCallback: ConnectivityManager.NetworkCallback? = null

    init {
        scope.launch {
            val saved = preferences.localAiModelId.first()
            applyModel(
                LocalAiCatalog.byId(saved) ?: detectOnDisk() ?: LocalAiCatalog.recommended,
                announce = false,
            )
            preferences.localAiModelId.collect { id ->
                LocalAiCatalog.byId(id)?.let { applyModel(it, announce = true) }
            }
        }
    }

    /** Persist a new choice — the flow collector above swaps to it. */
    suspend fun select(id: String) {
        if (LocalAiCatalog.byId(id) != null) preferences.setLocalAiModelId(id)
    }

    /**
     * The file downloaded fine but MediaPipe couldn't load it on this device
     * (a `.litertlm` an older runtime doesn't understand, a corrupt file).
     * Surface it so the driver picks another model instead of getting silent
     * "modo básico" answers.
     */
    fun reportModelUnusable() {
        _status.value = LocalAiStatus.DownloadFailed("Este modelo no funciona en tu móvil. Prueba otro.")
    }

    private fun applyModel(target: LocalAiModel, announce: Boolean) {
        if (announce && target.id == model.id) return
        downloadJob?.cancel()
        model = target
        _selectedId.value = target.id
        cleanupOtherFiles()
        refreshStatus()
        if (announce) {
            _modelChanged.tryEmit(Unit)
            // The driver picked this in Settings — that's the consent to fetch.
            if (_status.value == LocalAiStatus.ModelDownloadable) fetch()
        }
    }

    /** Delete any model file that isn't the current one — only one is kept. */
    private fun cleanupOtherFiles() {
        runCatching {
            modelDir.listFiles()?.forEach { f ->
                if (f.name != model.fileName && f.name != "${model.fileName}.part") f.delete()
            }
        }
    }

    private fun detectOnDisk(): LocalAiModel? = LocalAiCatalog.models.firstOrNull { m ->
        val f = File(modelDir, m.fileName)
        f.isFile && f.length() >= MIN_PLAUSIBLE_BYTES && (m.sizeBytes <= 0L || f.length() == m.sizeBytes)
    }

    /** Cheap re-evaluation of resting state; safe to call repeatedly. */
    fun refreshStatus() {
        _status.value = when {
            isModelComplete() -> LocalAiStatus.ReadyLocalModel
            downloadJob?.isActive == true -> _status.value
            awaitingUnmetered -> LocalAiStatus.WaitingForWifi
            else -> LocalAiStatus.ModelDownloadable
        }
    }

    /** The current model's file, or null until a verified copy is on disk. */
    fun modelFile(): File? = modelFile.takeIf { isModelComplete() }

    private fun isModelComplete(): Boolean =
        modelFile.isFile &&
            modelFile.length() >= MIN_PLAUSIBLE_BYTES &&
            (expectedSize <= 0L || modelFile.length() == expectedSize)

    /**
     * Starts (or resumes) the download of the current model. No-op if already
     * complete or in flight. Defers to Wi-Fi when the network is metered.
     */
    fun fetch() {
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
        val downloading = model
        _status.value = LocalAiStatus.Downloading(0f)
        try {
            modelDir.mkdirs()
            val resumeFrom = if (partFile.isFile) partFile.length() else 0L

            if (expectedSize > 0L && resumeFrom >= expectedSize) {
                finalizePartFile()
                return
            }

            val request = Request.Builder()
                .url(downloading.url)
                .apply { if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-") }
                .build()

            client.newCall(request).execute().use { response ->
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
                            currentCoroutineContext().ensureActive()
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
            _status.value = LocalAiStatus.DownloadFailed(e.message ?: "error de red")
        }
    }

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
        _modelChanged.tryEmit(Unit)
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
