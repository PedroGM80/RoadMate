package dev.pgm.roadmate.car

import android.content.Intent
import android.content.res.Configuration
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.Session
import dev.pgm.roadmate.BuildConfig
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.CurrentPlaceRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import dev.pgm.roadmate.domain.repository.PcmTranscriber
import dev.pgm.roadmate.domain.repository.RoutingRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.presentation.map.OfflineMapController
import dev.pgm.roadmate.utils.PermissionManager

class RoadMateSession(
    private val generateResponseUseCase: GenerateResponseUseCase,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val permissionManager: PermissionManager,
    private val speechSynthesisRepository: SpeechSynthesisRepository,
    private val pcmTranscriber: PcmTranscriber,
    private val currentPlaceRepository: CurrentPlaceRepository,
    private val mapSearchCoordinator: MapSearchCoordinator,
    private val preferences: AssistantPreferencesRepository,
    private val gemini: GeminiRepository,
    private val offlineMap: OfflineMapController,
    private val routingRepository: RoutingRepository,
) : Session() {

    /**
     * One renderer for the whole session, not one per screen.
     *
     * Pushing a screen moves the one below it to STOPPED, so a per-screen
     * surface callback would tear the map down every time the driver opened
     * a sub-screen and rebuild it on the way back — a visible flash and a
     * fresh style load each time. The host simply stops handing out a surface
     * while a template without a map is on top, which the renderer already
     * handles.
     */
    private val mapRenderer: CarMapRenderer by lazy {
        CarMapRenderer(
            carContext,
            BuildConfig.MAP_STYLE_URL,
            locationRepository,
            currentPlaceRepository,
        )
    }

    /**
     * The car switched between day and night. Hosts that recreate the drawing
     * surface on this change re-run the renderer's setup anyway; this covers
     * the ones that only send a config change, so the map still re-themes.
     */
    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        mapRenderer.refreshDayNight()
    }

    override fun onCreateScreen(intent: Intent): Screen {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(mapRenderer)
        return HomeCarScreen(
            carContext,
            mapRenderer,
            generateResponseUseCase,
            locationRepository,
            weatherRepository,
            permissionManager,
            speechSynthesisRepository,
            pcmTranscriber,
            currentPlaceRepository,
            mapSearchCoordinator,
            preferences,
            gemini,
            offlineMap,
            routingRepository,
        )
    }
}
