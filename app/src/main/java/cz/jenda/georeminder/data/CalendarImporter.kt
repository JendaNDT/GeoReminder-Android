package cz.jenda.georeminder.data

import android.content.Context
import android.provider.CalendarContract
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat

data class CalendarEventItem(
    val id: Long,
    val title: String,
    val startTimeMillis: Long,
    val location: String?,
)

object CalendarImporter {
    /** Načte nadcházející události ze systémového kalendáře (příštích 30 dní). */
    fun getUpcomingEvents(context: Context): List<CalendarEventItem> {
        val events = mutableListOf<CalendarEventItem>()
        try {
            val now = System.currentTimeMillis()
            val future = now + 30L * 24 * 3600 * 1000 // 30 dní dopředu

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.EVENT_LOCATION
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DELETED} = 0"
            val selectionArgs = arrayOf(now.toString(), future.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val title = cursor.getString(titleIdx) ?: "Událost"
                    val start = cursor.getLong(startIdx)
                    val loc = cursor.getString(locIdx)

                    events.add(CalendarEventItem(id, title, start, loc))
                }
            }
        } catch (_: SecurityException) {
            // Oprávnění READ_CALENDAR nebylo uděleno
        } catch (_: Exception) {}

        return events
    }

    /** Převádí událost z kalendáře na Reminder model. */
    fun toReminder(event: CalendarEventItem): Reminder {
        return Reminder(
            title = event.title,
            kind = ReminderKind.TIME,
            dueDate = event.startTimeMillis,
            timeRepeat = TimeRepeat.NEVER,
            placeName = event.location ?: "",
        )
    }
}
