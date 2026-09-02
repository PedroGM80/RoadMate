package dev.pgm.roadmate.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.model.CalendarEvent
import dev.pgm.roadmate.domain.repository.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CalendarRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CalendarRepository {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun eventsBetween(fromMillis: Long, toMillis: Long): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { query(fromMillis, toMillis) }
                .onFailure { Log.w(TAG, "calendar query failed", it) }
                .getOrDefault(emptyList())
        }
    }

    private fun query(from: Long, to: Long): List<CalendarEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val out = ArrayList<CalendarEvent>()
        context.contentResolver.query(
            uri, projection, null, null, CalendarContract.Instances.BEGIN + " ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(0)?.trim().takeUnless { it.isNullOrEmpty() } ?: continue
                out += CalendarEvent(
                    title = title,
                    startMillis = c.getLong(1),
                    endMillis = c.getLong(2),
                    allDay = c.getInt(3) == 1,
                    location = c.getString(4)?.trim().takeUnless { it.isNullOrEmpty() },
                )
            }
        }
        return out
    }

    private companion object {
        const val TAG = "CalendarRepository"
    }
}
