package dev.pgm.roadmate.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import dagger.hilt.android.AndroidEntryPoint
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
    lateinit var generateResponseUseCase: GenerateResponseUseCase

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    @Inject
    lateinit var permissionManager: PermissionManager

    @Inject
    lateinit var speechSynthesisRepository: SpeechSynthesisRepository

    @Inject
    lateinit var pcmTranscriber: PcmTranscriber

    @Inject
    lateinit var currentPlaceRepository: CurrentPlaceRepository

    @Inject
    lateinit var mapSearchCoordinator: MapSearchCoordinator

    @Inject
    lateinit var preferences: AssistantPreferencesRepository

    @Inject
    lateinit var gemini: GeminiRepository

    @Inject
    lateinit var offlineMap: OfflineMapController

    @Inject
    lateinit var routingRepository: RoutingRepository

    /**
     * Who is allowed to bind to this service and drive RoadMate's car screens.
     *
     * ALLOW_ALL_HOSTS es lo que permite que el Desktop Head Unit (DHU) se
     * conecte durante el desarrollo — pero también deja que *cualquier* app
     * del movil se enlace y maneje el asistente, incluidas las llamadas. Es
     * una comodidad de la build de debug, no algo que se publique, asi que la
     * build de release usa la allowlist por firma de la propia Car App
     * Library (solo hosts de Android Auto y Automotive OS).
     *
     * OJO con las pruebas: la opcion "Fuentes desconocidas" de Android Auto
     * NO aplica a las apps de Car App Library. En un coche real la app tiene
     * que venir de una fuente de confianza (Google Play; lo mas rapido es
     * Internal App Sharing o el canal de pruebas internas). Instalarla con
     * `adb install` / `installDebug` nunca la hara aparecer en el coche.
     * Para desarrollo, usar el Desktop Head Unit.
     * https://developer.android.com/training/cars/testing
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
