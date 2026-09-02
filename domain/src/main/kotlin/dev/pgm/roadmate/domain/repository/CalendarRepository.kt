package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.CalendarEvent

/**
 * Read-only view of the device calendar for "¿qué tengo hoy?" /
 * "¿cuál es mi próxima cita?". Local (CalendarContract), no account of ours.
 * Returns an empty list / null without the READ_CALENDAR permission.
 */
interface CalendarRepository {

    fun hasPermission(): Boolean

    /** Events overlapping [fromMillis]..[toMillis], earliest first. */
    suspend fun eventsBetween(fromMillis: Long, toMillis: Long): List<CalendarEvent>
}
