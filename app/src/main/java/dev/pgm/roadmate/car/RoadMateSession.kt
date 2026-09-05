package dev.pgm.roadmate.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.CurrentPlaceRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import dev.pgm.roadmate.domain.repository.PcmTranscriber
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
) : Session() {

    override fun onCreateScreen(intent: Intent): Screen = HomeCarScreen(
        carContext,
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
    )
}
