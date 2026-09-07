package cz.jenda.georeminder.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Jeden konkrétní výskyt události z CalendarContract.Instances. */
data class CalendarEventItem(
    val eventId: Long,
    val instanceKey: String,
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val location: String?,
    val allDay: Boolean,
)

sealed interface CalendarLoadResult {
    data class Success(val events: List<CalendarEventItem>) : CalendarLoadResult
    data object Empty : CalendarLoadResult
    data object PermissionDenied : CalendarLoadResult
    data class Error(val reason: String) : CalendarLoadResult
}

object CalendarImporter {
    private const val WINDOW_DAYS = 30L

    /**
     * Načte skutečné výskyty událostí na příštích 30 dní přes Instances.
     * Opakované kalendářové události jsou tak rozbalené na konkrétní termíny.
     */
    suspend fun getUpcomingEvents(context: Context): CalendarLoadResult =
        withContext(Dispatchers.IO) {
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext CalendarLoadResult.PermissionDenied
            }

            try {
                val now = System.currentTimeMillis()
                val future = now + WINDOW_DAYS * 24L * 60L * 60L * 1000L
                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(builder, now)
                ContentUris.appendId(builder, future)

                val projection = arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.ALL_DAY,
                )

                val byInstance = linkedMapOf<String, CalendarEventItem>()
                context.contentResolver.query(
                    builder.build(),
                    projection,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { cursor ->
                    val eventIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                    val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                    val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                    val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                    val locationIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                    val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)

                    while (cursor.moveToNext()) {
                        val eventId = cursor.getLong(eventIdIdx)
                        val begin = cursor.getLong(beginIdx)
                        if (begin < now || begin > future) continue

                        val key = sourceKey(eventId, begin)
                        byInstance[key] = CalendarEventItem(
                            eventId = eventId,
                            instanceKey = key,
                            title = cursor.getString(titleIdx)?.takeIf { it.isNotBlank() } ?: "Událost",
                            startTimeMillis = begin,
                            endTimeMillis = cursor.getLong(endIdx),
                            location = cursor.getString(locationIdx)?.takeIf { it.isNotBlank() },
                            allDay = cursor.getInt(allDayIdx) != 0,
                        )
                    }
                }

                val events = byInstance.values.sortedBy { it.startTimeMillis }
                if (events.isEmpty()) CalendarLoadResult.Empty
                else CalendarLoadResult.Success(events)
            } catch (_: SecurityException) {
                CalendarLoadResult.PermissionDenied
            } catch (e: Exception) {
                android.util.Log.w("CalendarImporter", "Načtení instancí kalendáře selhalo", e)
                CalendarLoadResult.Error(e.javaClass.simpleName)
            }
        }

    fun sourceKey(eventId: Long, beginMillis: Long): String = "$eventId:$beginMillis"

    /** Převádí konkrétní instanci kalendáře na jednorázový časový reminder. */
    fun toReminder(event: CalendarEventItem): Reminder = Reminder(
        title = event.title,
        kind = ReminderKind.TIME,
        dueDate = event.startTimeMillis,
        timeRepeat = TimeRepeat.NEVER,
        placeName = event.location.orEmpty(),
        calendarSourceKey = event.instanceKey,
    )
}
