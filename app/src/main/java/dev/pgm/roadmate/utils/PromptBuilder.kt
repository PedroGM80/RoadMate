package dev.pgm.roadmate.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Assembles the text prompt sent to Gemini Nano from the current trip context.
 */
object PromptBuilder {

    fun build(
        currentLocation: Pair<Double, Double>?,
        destination: String?,
        userInput: String,
        tripContext: String? = null,
        timestamp: Date = Date()
    ): String {
        val formattedDateTime = DATE_FORMAT.format(timestamp)

        return buildString {
            appendLine("Eres el asistente de conducción de RoadMate.")
            appendLine("Fecha y hora actual: $formattedDateTime")

            if (currentLocation != null) {
                appendLine("Ubicación actual (lat, lon): ${currentLocation.first}, ${currentLocation.second}")
            } else {
                appendLine("Ubicación actual: no disponible")
            }

            if (!destination.isNullOrBlank()) {
                appendLine("Destino del viaje: $destination")
            }

            if (!tripContext.isNullOrBlank()) {
                appendLine("Contexto del viaje: $tripContext")
            }

            appendLine()
            appendLine("Petición del usuario:")
            append(userInput.trim())
        }
    }

    private val DATE_FORMAT = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.getDefault())
}
