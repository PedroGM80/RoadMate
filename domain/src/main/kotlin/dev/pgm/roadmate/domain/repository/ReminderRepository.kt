package dev.pgm.roadmate.domain.repository

/**
 * "recuérdame llamar al taller en media hora" — schedules a spoken +
 * notified reminder. Local only (AlarmManager); nothing leaves the device.
 * Best effort: an inexact alarm, and a reminder set before a reboot is lost.
 */
interface ReminderRepository {

    suspend fun schedule(text: String, whenEpochMillis: Long)
}
