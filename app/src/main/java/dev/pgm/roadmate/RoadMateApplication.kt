package dev.pgm.roadmate

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import dev.pgm.roadmate.domain.repository.GeminiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import javax.inject.Inject

@HiltAndroidApp
class RoadMateApplication : Application() {

    @Inject
    lateinit var geminiRepository: GeminiRepository

    /** Process-lived: the work it runs must outlast any screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Required once before any MapView / OfflineManager use. No API key —
        // the tile/style source (OpenFreeMap) needs none.
        MapLibre.getInstance(this)
    }

    /**
     * Give the local model's memory back when the system asks for it.
     *
     * RoadMate keeps a whole LLM resident — 0.5 GB for Qwen2.5-0.5B, ~1.6 GB
     * for the 1.5B build. On a mid-range phone that makes it the first thing
     * the OS reclaims, and being killed outright means the driver loses the
     * assistant in the middle of a trip. Releasing it deliberately costs one
     * cold start on the next question instead.
     *
     * Only from UI_HIDDEN upward: those are the levels that mean "you are in
     * the background and the system wants room". Trimming while the driver is
     * looking at the app would throw away the warm-up they are about to use.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
        if (!::geminiRepository.isInitialized) return
        appScope.launch {
            if (geminiRepository.releaseLocalAiMemory()) {
                Log.i(TAG, "released the local model after onTrimMemory($level)")
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (!::geminiRepository.isInitialized) return
        appScope.launch { geminiRepository.releaseLocalAiMemory() }
    }

    private companion object {
        const val TAG = "RoadMateApplication"
    }
}
