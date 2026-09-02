package dev.pgm.roadmate.domain.model

/** One entry from the device calendar, times as epoch millis. */
data class CalendarEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String? = null,
)
