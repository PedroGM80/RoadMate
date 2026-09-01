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
import androidx.lifecycle.lifecycleScope
import dev.pgm.roadmate.R
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.domain.usecase.RecordAudioUseCase
import dev.pgm.roadmate.utils.PermissionManager
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
            val userInput = recordAudioUseCase.finalText()
            if (userInput.isBlank()) {
                statusText = "No te he oído. Pulsa Escuchar y prueba otra vez."
                isBusy = false
                invalidate()
                return@launch
            }
            lastRecognizedInput = userInput

            val location = locationRepository.getCurrentCoordinates()
            val calendar = Calendar.getInstance()
            val travelContext = TravelContext(
                currentLocation = location,
                hour = calendar.get(Calendar.HOUR_OF_DAY),
                date = calendar.time,
                userInput = userInput
            )

            generateResponseUseCase(travelContext, userInput).collect { response ->
                statusText = response
                isBusy = false
                invalidate()
            }
        }
    }
}
