package dev.glass.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CalendarEvent(val title: String, val startsAt: Long)

object CalendarRepository {
    fun upcoming(context: Context, date: LocalDate = LocalDate.now()): List<CalendarEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString())
            .appendPath(end.toString())
            .build()
        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            while (cursor.moveToNext() && events.size < MAX_EVENTS) {
                val title = cursor.getString(titleIndex)?.takeIf(String::isNotBlank) ?: "Untitled event"
                events += CalendarEvent(title, cursor.getLong(beginIndex))
            }
        }
        return events
    }

    fun eventTimeLabel(startsAt: Long): String =
        java.time.format.DateTimeFormatter.ofPattern("EEE, h:mm a")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(startsAt))

    private const val MAX_EVENTS = 4
}
