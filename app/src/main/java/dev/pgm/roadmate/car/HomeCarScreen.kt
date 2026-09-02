package dev.pgm.roadmate.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import android.util.Log
import androidx.lifecycle.lifecycleScope
import dev.pgm.roadmate.R
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.domain.usecase.RecordAudioUseCase
import dev.pgm.roadmate.utils.PermissionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Single-screen voice Q&A UI for the car display: a status row and one
 * "Escuchar" action, matching the same golden path as HomeScreen on the phone
 * (record -> transcribe -> ask Gemini -> speak), but through PaneTemplate
 * instead of Compose, since Android Auto only renders host-controlled
 * templates. Rest-reminder silence detection isn't surfaced here — it still
 * runs via SilenceDetectionForegroundService/RoadMateViewModel when this
 * screen isn't the one in front.
 *
 * Listening/processing collapses into the host's native loading spinner
 * (Pane.setLoading) rather than juggling separate "Escuchando.../
 * Procesando..." row text — one generic busy state reads faster at a glance,
 * which matters more here than on the phone.
 */
class HomeCarScreen(
    carContext: CarContext,
    private val recordAudioUseCase: RecordAudioUseCase,
    private val generateResponseUseCase: GenerateResponseUseCase,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val permissionManager: PermissionManager
) : Screen(carContext) {

    private var statusText = "Pulsa Escuchar y pregunta."
    private var lastRecognizedInput: String? = null
    private var isBusy = false

    private val header = Header.Builder()
        .setTitle("RoadMate")
        .setStartHeaderAction(Action.APP_ICON)
        .build()

    private val micIcon = CarIcon.Builder(
        IconCompat.createWithResource(carContext, R.drawable.lucide_ic_mic)
    ).setTint(CarColor.DEFAULT).build()

    override fun onGetTemplate(): Template {
        if (!permissionManager.hasRecordAudioPermission()) {
            return PaneTemplate.Builder(
                Pane.Builder()
                    .addRow(
                        Row.Builder()
                            .setImage(micIcon)
                            .setTitle("Permisos pendientes")
                            .addText("Abre RoadMate en el móvil una vez para dar permiso de micrófono y ubicación.")
                            .build()
                    )
                    .build()
            )
                .setHeader(header)
                .build()
        }

        val pane = if (isBusy) {
            Pane.Builder().setLoading(true).build()
        } else {
            Pane.Builder()
                .addRow(
                    Row.Builder()
                        .setImage(micIcon)
                        .setTitle(statusText)
                        .apply { lastRecognizedInput?.let { addText("Tú: “$it”") } }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setTitle("Escuchar")
                        .setIcon(micIcon)
                        .setOnClickListener(::startListening)
                        .build()
                )
                .build()
        }

        return PaneTemplate.Builder(pane)
            .setHeader(header)
            .build()
    }

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
                val userInput = recordAudioUseCase.finalText()
                if (userInput.isBlank()) {
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
                if (!answered) statusText = carContext.getString(R.string.car_no_answer)
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
    }
}
