package dev.pgm.roadmate.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import dagger.hilt.android.AndroidEntryPoint
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.domain.usecase.RecordAudioUseCase
import dev.pgm.roadmate.utils.PermissionManager
import javax.inject.Inject

/**
 * Entry point Android Auto discovers and binds to when the phone is connected.
 * Car App Library's Session/Screen classes have no Hilt entry point of their own
 * (unlike Activity/Service), so this Service — which does — receives everything
 * via field injection and passes it down manually into [RoadMateSession].
 *
 * ALLOW_ALL_HOSTS_VALIDATOR is fine for sideloading onto your own car head unit
 * with Android Auto's "Unknown sources" developer option enabled; tighten this
 * before any real distribution.
 */
@AndroidEntryPoint
class RoadMateCarAppService : CarAppService() {

    @Inject
    lateinit var recordAudioUseCase: RecordAudioUseCase

    @Inject
    lateinit var generateResponseUseCase: GenerateResponseUseCase

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var permissionManager: PermissionManager

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session =
        RoadMateSession(recordAudioUseCase, generateResponseUseCase, locationRepository, permissionManager)
}
