package dev.pgm.roadmate

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre

@HiltAndroidApp
class RoadMateApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Required once before any MapView / OfflineManager use. No API key —
        // the tile/style source (OpenFreeMap) needs none.
        MapLibre.getInstance(this)
    }
}
