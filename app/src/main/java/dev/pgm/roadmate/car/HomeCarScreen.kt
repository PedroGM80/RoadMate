package dev.pgm.roadmate.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import dev.pgm.roadmate.R
import dev.pgm.roadmate.domain.model.TravelContext
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds

/**
 * Voice Q&A on the car display: the answer as the message body, one primary
 * "Escuchar" action, and the driver's current position under it. Same golden
 * path as HomeScreen on the phone (record -> transcribe -> ask Gemini ->
 * speak), through a host template instead of Compose, since Android Auto only
 * renders host-controlled templates.
 *
 * MessageTemplate, not PaneTemplate. PaneTemplate is built for a short list of
 * labelled detail rows next to up to two actions; putting a whole spoken
 * answer in a Row title made the host lay it out as one giant scrollable row
 * with paging chevrons down the side, truncate it while driving restrictions
 * are on (Row titles are single-line under restriction), and float the action
 * in the middle of the empty space. MessageTemplate is the shape the content
 * actually has — one block of prose, an icon, and buttons anchored at the
 * bottom — and the host handles driving-restricted truncation of a *message*
 * by itself.
 *
 * The microphone comes from the host via [CarMicAudioSource], never from
 * `AudioRecord` — see that class for why the phone's mic is unreachable here.
 *
 * Rest-reminder silence detection isn't surfaced here — it still runs via
 * SilenceDetectionForegroundService/RoadMateViewModel when this screen isn't
 * the one in front.
 */
class HomeCarScreen(
    carContext: CarContext,
    private val renderer: CarMapRenderer,
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
) : Screen(carContext) {

    private var statusText = carContext.getString(R.string.car_idle)

    /** What the mic understood, echoed back so a misheard question is obvious. */
    private var lastRecognizedInput: String? = null
    private var isBusy = false

    private val micIcon = carIcon(R.drawable.lucide_ic_mic, CarColor.DEFAULT)

    // The lucide vectors are stroked in black, so they only look right once the
    // host recolours them. CarColor.DEFAULT is the host's own foreground colour
    // and is what buttons and rows expect.
    //
    // The big icon MessageTemplate draws above the message is a different
    // case: DEFAULT left it black on the near-black template surface, and
    // PRIMARY resolves to the app's own primary — also dark — so it stayed
    // invisible. createCustom names both variants outright, so the host picks
    // one that contrasts whichever mode it is in instead of us guessing.
    private val micIconLarge = carIcon(
        R.drawable.lucide_ic_mic,
        CarColor.createCustom(MIC_TINT_DAY, MIC_TINT_NIGHT),
    )
    private val stopIcon = carIcon(R.drawable.lucide_ic_square, CarColor.DEFAULT)
    private val mapIcon = carIcon(R.drawable.lucide_ic_map, CarColor.DEFAULT)
    private val settingsIcon = carIcon(R.drawable.lucide_ic_settings, CarColor.DEFAULT)

    init {
        // A template is a snapshot: it observes nothing, so anything that
        // changes what it should say has to invalidate it.
        //
        // Sparingly, though. The host meters templates — the DHU's "Permits"
        // counter is that budget — and an app that redraws on every emission
        // burns through it and is dropped as unresponsive. So: only signals
        // that actually change a *rendered string*, and only when the string
        // itself changes.
        lifecycleScope.launch {
            // StateFlow already conflates equal values, so this fires only on
            // the actual start/stop transitions.
            speechSynthesisRepository.isSpeaking.collect { invalidate() }
        }
        // The location line is polled rather than driven by the GPS flow. On
        // the move it would otherwise change several times a second, and every
        // change is a template the host has to accept — the budget the DHU
        // shows as "Permits". A 15-second check keeps the header honest at a
        // cost of at most four redraws a minute.
        lifecycleScope.launch {
            var shown = locationLine()
            while (isActive) {
                delay(LOCATION_REFRESH_MS.milliseconds)
                val current = locationLine()
                if (current != shown) {
                    shown = current
                    invalidate()
                }
            }
        }
        lifecycleScope.launch { runCatching { locationRepository.getCurrentCoordinates() } }
    }

    /**
     * The map is the backdrop, the voice card sits on it.
     *
     * A MessageTemplate on its own left the driver looking at a black screen
     * with a sentence in the middle of it, and its own action strip — the only
     * place a third control fits — was not being drawn. Wrapped in
     * [MapWithContentTemplate] the map is always there, the settings icon has
     * a strip the host actually renders, and the map controls come along for
     * free.
     */
    override fun onGetTemplate(): Template = MapWithContentTemplate.Builder()
        .setContentTemplate(voiceCard())
        .setMapController(
            MapController.Builder()
                .setMapActionStrip(
                    ActionStrip.Builder()
                        .addAction(Action.PAN)
                        .addAction(mapIconAction(R.drawable.lucide_ic_locate_fixed) { renderer.centreOnDriver() })
                        .addAction(mapIconAction(R.drawable.lucide_ic_zoom_in) { renderer.zoomIn() })
                        .addAction(mapIconAction(R.drawable.lucide_ic_zoom_out) { renderer.zoomOut() })
                        .build()
                )
                .build()
        )
        .setActionStrip(settingsStrip())
        .build()

    private fun voiceCard(): Template {
        if (!permissionManager.hasRecordAudioPermission()) {
            return MessageTemplate.Builder(
                carContext.getString(R.string.car_permissions_message)
            )
                .setHeader(header())
                .setIcon(micIconLarge)
                .build()
        }

        if (isBusy) {
            // The host draws its own spinner; the message is only what it falls
            // back to if it decides to show text alongside it.
            return MessageTemplate.Builder(carContext.getString(R.string.car_listening))
                .setHeader(header())
                .setLoading(true)
                .build()
        }

        return MessageTemplate.Builder(messageBody())
            .setHeader(header())
            .setIcon(micIconLarge)
            // Icons only, no labels: a driver gets a glance, and a recognised
            // glyph reads faster than a word — which is also why the host
            // allows a title-less action. FLAG_PRIMARY is what gets this one
            // the filled treatment; without it both buttons render identically
            // and neither reads as "the thing to press".
            .addAction(
                Action.Builder()
                    .setIcon(micIcon)
                    .setFlags(Action.FLAG_PRIMARY)
                    .setOnClickListener(::startListening)
                    .build()
            )
            // The second slot is whichever the driver needs at that moment:
            // cutting off a long answer beats everything while it is being
            // read, and the way to the places list the rest of the time.
            .addAction(if (isSpeaking()) stopAction() else mapAction())
            .build()
    }

    private fun mapIconAction(iconRes: Int, onClick: () -> Unit): Action = Action.Builder()
        .setIcon(carIcon(iconRes, CarColor.DEFAULT))
        .setOnClickListener(onClick)
        .build()

    /**
     * Settings, as an icon in the top corner — on the *outer* template's
     * strip. A MessageTemplate's own header end actions are drawn by Android
     * Auto but never deliver the click (verified with a log on the listener),
     * and its two action slots are spoken for by the mic and the contextual
     * second control, so this is the only place a third one both renders and
     * responds.
     */
    private fun settingsStrip(): ActionStrip = ActionStrip.Builder()
        .addAction(
            Action.Builder()
                .setIcon(settingsIcon)
                .setOnClickListener { screenManager.push(settingsScreen()) }
                .build()
        )
        .build()

    private fun settingsScreen(): SettingsCarScreen = SettingsCarScreen(
        carContext,
        preferences,
        gemini,
        offlineMap,
        locationRepository,
    )

    /** No end actions in the header — see [settingsStrip] for why. */
    private fun header(): Header = Header.Builder()
        // The header title, not the message body: the host caps how many lines
        // of a MessageTemplate message it will draw (a third line came out as
        // "…"), and the header is the one part that survives every restriction
        // level. The app icon in the start slot still says which app this is.
        .setTitle(locationLine())
        .setStartHeaderAction(Action.APP_ICON)
        .build()

    private fun mapScreen(): NavigationCarScreen = NavigationCarScreen(
        carContext,
        renderer,
        locationRepository,
        currentPlaceRepository,
        mapSearchCoordinator,
        routingRepository,
        speechSynthesisRepository,
        offlineMap,
    )

    /**
     * The answer alone leaves the driver guessing when speech recognition got
     * the question wrong, which is most of what goes wrong in a moving car.
     * Echoing the transcript above the answer makes a misheard question
     * self-evident.
     */
    private fun messageBody(): String = buildString {
        lastRecognizedInput?.let { append("“").append(it).append("”\n\n") }
        append(statusText)
    }

    /**
     * Where the driver is, for the header — the same thing the phone shows in
     * its location chip. The street name when the phone's offline map has
     * resolved one, the raw fix otherwise, and the app's own name only when
     * there is no fix at all.
     */
    private fun locationLine(): String {
        currentPlaceRepository.label.value?.let { return it }
        val here = locationRepository.location.value
            ?: return carContext.getString(R.string.app_name)
        return carContext.getString(R.string.car_map_coordinates, here.first, here.second)
    }

    private fun isSpeaking(): Boolean = speechSynthesisRepository.isSpeaking.value

    private fun stopAction(): Action = Action.Builder()
        .setIcon(stopIcon)
        .setOnClickListener {
            speechSynthesisRepository.stop()
            invalidate()
        }
        .build()

    private fun mapAction(): Action = Action.Builder()
        .setIcon(mapIcon)
        .setOnClickListener { screenManager.push(mapScreen()) }
        .build()

    private fun carIcon(resId: Int, tint: CarColor): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId))
            .setTint(tint)
            .build()

    private fun startListening() {
        if (isBusy) return
        isBusy = true
        invalidate()

        lifecycleScope.launch {
            // `isBusy` collapses to the host's loading spinner, and the only
            // way out of it is this coroutine. Anything that throws — the mic
            // refused, the model, the weather lookup — used to leave the car
            // screen spinning forever with no button to press. The finally is
            // the whole point.
            try {
                // Silence any answer still being read and let the output buffer
                // drain before opening the mic: in a car the speaker sits next
                // to the microphone, and the recogniser will happily transcribe
                // RoadMate's own voice as the next question.
                speechSynthesisRepository.stop()
                speechSynthesisRepository.awaitDoneSpeaking()

                val userInput = pcmTranscriber.transcribe(CarMicAudioSource(carContext))
                if (userInput.isBlank()) {
                    lastRecognizedInput = null
                    statusText = carContext.getString(R.string.car_not_heard)
                    return@launch
                }
                lastRecognizedInput = userInput

                val location = locationRepository.getCurrentCoordinates()
                // Same weather the phone would have: without it "¿qué tiempo
                // hace?" answered "no puedo consultarlo" in the car even where
                // the handset could.
                val weather = location?.let { (lat, lon) ->
                    runCatching { weatherRepository.getCurrentWeatherDescription(lat, lon) }.getOrNull()
                }
                val calendar = Calendar.getInstance()
                val travelContext = TravelContext(
                    currentLocation = location,
                    hour = calendar.get(Calendar.HOUR_OF_DAY),
                    minute = calendar.get(Calendar.MINUTE),
                    date = calendar.time,
                    userInput = userInput,
                    weatherDescription = weather,
                )

                var answered = false
                generateResponseUseCase(travelContext, userInput).collect { response ->
                    answered = true
                    statusText = response
                    isBusy = false
                    invalidate()
                }
                if (!answered) {
                    statusText = carContext.getString(R.string.car_no_answer)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "car question failed", t)
                statusText = carContext.getString(R.string.car_error)
            } finally {
                isBusy = false
                invalidate()
            }
        }
    }

    private companion object {
        const val TAG = "HomeCarScreen"

        /** How often the header re-checks where the car is. */
        const val LOCATION_REFRESH_MS = 15_000L

        /** RoadMate blue on a light surface, plain white on a dark one. */
        const val MIC_TINT_DAY = 0xFF1A73E8.toInt()
        const val MIC_TINT_NIGHT = 0xFFFFFFFF.toInt()
    }
}
