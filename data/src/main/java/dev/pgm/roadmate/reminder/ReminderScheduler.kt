package dev.pgm.roadmate.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.repository.ReminderRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers reminders with [AlarmManager]. Inexact and wake-while-idle — a
 * "recuérdame en 20 minutos" doesn't need second precision and this needs no
 * special exact-alarm permission.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderRepository {

    override suspend fun schedule(text: String, whenEpochMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TEXT, text)
        val pending = PendingIntent.getBroadcast(
            context,
            (whenEpochMillis / 1000L).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenEpochMillis, pending)
    }
}
