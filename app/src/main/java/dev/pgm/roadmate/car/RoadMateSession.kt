package dev.pgm.roadmate.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.domain.usecase.RecordAudioUseCase
import dev.pgm.roadmate.utils.PermissionManager

class RoadMateSession(
    private val recordAudioUseCase: RecordAudioUseCase,
    private val generateResponseUseCase: GenerateResponseUseCase,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val permissionManager: PermissionManager
) : Session() {

    override fun onCreateScreen(intent: Intent): Screen = HomeCarScreen(
        carContext,
        recordAudioUseCase,
        generateResponseUseCase,
        locationRepository,
        weatherRepository,
        permissionManager,
    )
}
