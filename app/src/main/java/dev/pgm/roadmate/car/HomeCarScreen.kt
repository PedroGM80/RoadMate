package dev.pgm.roadmate.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.domain.usecase.RecordAudioUseCase
import dev.pgm.roadmate.utils.PermissionManager
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Single-screen voice Q&A UI for the car display: a status line and one
 * "Escuchar" action, matching the same golden path as HomeScreen on the phone
 * (record -> transcribe -> ask Gemini -> speak), but through PaneTemplate
 * instead of Compose, since Android Auto only renders host-controlled
 * templates. Rest-reminder silence detection isn't surfaced here — it still
 * runs via SilenceDetectionForegroundService/RoadMateViewModel when this
 * screen isn't the one in front.
 */
class HomeCarScreen(
    carContext: CarContext,
    private val recordAudioUseCase: RecordAudioUseCase,
    private val generateResponseUseCase: GenerateResponseUseCase,
    private val locationRepository: LocationRepository,
    private val permissionManager: PermissionManager
) : Screen(carContext) {

    private var statusText = "Pulsa Escuchar y haz tu pregunta."
    private var isBusy = false

    private val header = Header.Builder().setStartHeaderAction(Action.APP_ICON).build()

    override fun onGetTemplate(): Template {
        if (!permissionManager.hasRecordAudioPermission()) {
            return PaneTemplate.Builder(
                Pane.Builder()
                    .addRow(
                        Row.Builder()
                            .setTitle("Permisos pendientes")
                            .addText("Abre RoadMate en tu teléfono una vez para conceder micrófono y ubicación.")
                            .build()
                    )
                    .build()
            )
                .setHeader(header)
                .build()
        }

        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(statusText).build())
            .addAction(
                Action.Builder()
                    .setTitle(if (isBusy) statusText else "Escuchar")
                    .setEnabled(!isBusy)
                    .setOnClickListener(::startListening)
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(header)
            .build()
    }

    private fun startListening() {
        if (isBusy) return
        isBusy = true
        statusText = "Escuchando..."
        invalidate()

        lifecycleScope.launch {
            val userInput = recordAudioUseCase()
            if (userInput.isBlank()) {
                statusText = "No te he oído. Pulsa Escuchar para volver a intentarlo."
                isBusy = false
                invalidate()
                return@launch
            }

            statusText = "Procesando..."
            invalidate()

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
