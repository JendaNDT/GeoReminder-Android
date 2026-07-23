package cz.jenda.georeminder.data

import android.content.Context
import android.provider.CalendarContract
import cz.jenda.georeminder.R
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarEventItem(
    val id: String,
    val title: String,
    val startTimeMillis: Long,
    val location: String?,
)

object CalendarImporter {
    /** Načte nadcházející události ze systémového kalendáře (příštích 30 dní). */
    suspend fun getUpcomingEvents(context: Context): List<CalendarEventItem> =
        withContext(Dispatchers.IO) {
        val events = mutableListOf<CalendarEventItem>()
        try {
            val now = System.currentTimeMillis()
            val future = now + 30L * 24 * 3600 * 1000 // 30 dní dopředu

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.EVENT_LOCATION
            )

            // Instances expanduje i opakované události; prostá tabulka Events
            // vracela jen definici recurrence a mohla ukázat chybný DTSTART.
            CalendarContract.Instances.query(
                context.contentResolver,
                projection,
                now,
                future,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val startIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)

                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(idIdx)
                    val start = cursor.getLong(startIdx)
                    val id = "$eventId:$start"
                    val title = cursor.getString(titleIdx)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: context.getString(R.string.calendar_default_event_title)
                    val loc = cursor.getString(locIdx)

                    events.add(CalendarEventItem(id, title, start, loc))
                }
            }
        } catch (_: SecurityException) {
            // Oprávnění READ_CALENDAR nebylo uděleno
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        events
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
