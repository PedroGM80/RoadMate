package dev.pgm.roadmate.routing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.model.RoutingDataStatus
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets BRouter `.rd5` segment tiles onto the device on demand: when a route
 * needs a tile that isn't present, download it (resumable) from brouter.de.
 * No account, one-off per area.
 *
 * Mobile data is allowed by default, and that is deliberate. Gating the
 * download on an unmetered network sounds prudent and is exactly backwards
 * for this app: the tile is only ever missing for an area the car is *in*,
 * and a car on the road is on mobile data. Wi-Fi-only meant every route
 * attempted from the driver's seat returned "no data for this area" — routing
 * appeared broken while being, from the code's point of view, correct. The
 * driver can turn mobile downloads off in settings and pre-fetch at home.
 *
 * Mirrors LocalAiModelManager's download style. [ensureTiles] is the whole
 * public surface for [BRouterRouter]; [status] drives a small progress hint
 * on the map.
 */
@Singleton
class RoutingDataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AssistantPreferencesRepository,
) {

    val segmentDir: File = File(context.filesDir, "brouter/segments")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val _status = MutableStateFlow<RoutingDataStatus>(RoutingDataStatus.Idle)
    val status: StateFlow<RoutingDataStatus> = _status.asStateFlow()

    /**
     * Ensures every named tile is present, downloading the missing ones.
     * Returns false if a tile is missing and can't be fetched (no connection,
     * mobile downloads turned off, or the download failed) — [status] says
     * which, so the caller can tell the driver something actionable instead of
     * a flat "no route".
     */
    suspend fun ensureTiles(names: List<String>): Boolean = withContext(Dispatchers.IO) {
        val missing = names.filterNot { tileFile(it).isReadableTile() }
        if (missing.isEmpty()) {
            _status.value = RoutingDataStatus.Ready
            return@withContext true
        }
        when (networkState()) {
            NetworkState.OFFLINE -> {
                _status.value = RoutingDataStatus.NoNetwork
                return@withContext false
            }
            NetworkState.METERED -> if (!allowsMobileDownload()) {
                _status.value = RoutingDataStatus.WaitingForWifi
                return@withContext false
            }
            NetworkState.UNMETERED -> Unit
        }

        segmentDir.mkdirs()
        for (name in missing) {
            val ok = runCatching { downloadTile(name) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "tile $name download failed", it)
                }
                .getOrDefault(false)
            if (!ok) {
                _status.value = RoutingDataStatus.Failed("no se pudo descargar el mapa de ruta")
                return@withContext false
            }
        }
        _status.value = RoutingDataStatus.Ready
        true
    }

    private suspend fun downloadTile(name: String): Boolean {
        val target = tileFile(name)
        val part = File(segmentDir, "$name.rd5.part")
        val resumeFrom = if (part.isFile) part.length() else 0L

        val request = Request.Builder()
            .url("$SEGMENTS_URL/$name.rd5")
            .apply { if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-") }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 416) { part.delete(); return false }
            if (!response.isSuccessful) return false
            val partial = response.code == 206
            if (!partial && part.exists()) part.delete()

            val body = response.body
            val total = body.contentLength().let {
                if (it > 0L) (if (partial) resumeFrom else 0L) + it else -1L
            }
            body.byteStream().use { input ->
                FileOutputStream(part, /* append = */ partial).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = if (partial) resumeFrom else 0L
                    var lastPct = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0L) {
                            val pct = ((done * 100L) / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                _status.value = RoutingDataStatus.Downloading(
                                    (done.toFloat() / total).coerceIn(0f, 1f),
                                )
                            }
                        }
                    }
                    output.fd.sync()
                }
            }
        }

        if (!part.isReadableTile()) { part.delete(); return false }
        if (target.exists()) target.delete()
        return part.renameTo(target)
    }

    private fun tileFile(name: String) = File(segmentDir, "$name.rd5")

    private fun File.isReadableTile() = isFile && length() >= MIN_TILE_BYTES

    /**
     * Reading the preference must never be what stops a route: a DataStore
     * that fails to open would otherwise throw here, on the routing path, for
     * a setting whose whole point is to be permissive by default.
     */
    private suspend fun allowsMobileDownload(): Boolean =
        runCatching { preferences.routeDataOverMobile.first() }.getOrDefault(true)

    private fun networkState(): NetworkState {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkState.OFFLINE
        val caps = runCatching { cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } }
            .getOrNull() ?: return NetworkState.OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkState.OFFLINE
        }
        // NOT_VALIDATED covers captive portals and a connection still coming
        // up. Treated as metered rather than offline: worth one attempt, since
        // validation lags reality on a moving phone handing between cells.
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            NetworkState.UNMETERED
        } else {
            NetworkState.METERED
        }
    }

    private enum class NetworkState { OFFLINE, METERED, UNMETERED }

    private companion object {
        const val SEGMENTS_URL = "https://brouter.de/brouter/segments4"
        const val MIN_TILE_BYTES = 100_000L
        const val TAG = "RoutingDataManager"
    }
}
