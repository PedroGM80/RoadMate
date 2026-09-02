package dev.pgm.roadmate.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import dagger.hilt.android.AndroidEntryPoint
import dev.pgm.roadmate.BuildConfig
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
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
 * Host validation is build-type dependent — see [createHostValidator].
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
    lateinit var weatherRepository: WeatherRepository

    @Inject
    lateinit var permissionManager: PermissionManager

    /**
     * Who is allowed to bind to this service and drive RoadMate's car screens.
     *
     * ALLOW_ALL_HOSTS is what makes sideloading onto your own head unit work
     * with Android Auto's "Unknown sources" developer option — but it also
     * lets *any* app on the phone bind and drive the assistant, including
     * placing calls. That is a debug-build convenience, not something to ship,
     * so a release build uses the Car App Library's own signature allowlist
     * (Android Auto and Automotive OS hosts only).
     */
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = RoadMateSession(
        recordAudioUseCase,
        generateResponseUseCase,
        locationRepository,
        weatherRepository,
        permissionManager,
    )
}
