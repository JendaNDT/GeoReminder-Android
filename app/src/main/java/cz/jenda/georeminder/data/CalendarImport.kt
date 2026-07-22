package cz.jenda.georeminder.data

import android.content.Context
import android.location.Geocoder
import android.provider.CalendarContract
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import cz.jenda.georeminder.model.ReminderKind
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Jednorázový import události z kalendáře (přes systémový CalendarContract –
 * funguje s Google Kalendářem i ostatními účty na zařízení). NEJDE o průběžnou
 * synchronizaci: převezme se název, čas začátku a případné místo, ze kterých se
 * předvyplní nová připomínka, a tím to končí.
 */
object CalendarImport {

    private const val TAG = "CalendarImport"

    /** Nadcházející událost z kalendáře. */
    data class Event(
        val title: String,
        val begin: Long,
        val location: String?,
        val allDay: Boolean,
        val calendar: String?,
    )

    /** Předvyplnění formuláře připomínky vzniklé z události. */
    data class Prefill(
        val title: String,
        val dueDate: Long,
        val kind: ReminderKind,
        val placeName: String,
        val coordinate: LatLng?,
    )

    /** Události od teď do +[days] dní, seřazené podle začátku. Volat na IO vlákně. */
    fun upcoming(context: Context, days: Int = 30): List<Event> {
        val now = System.currentTimeMillis()
        val end = now + days * 24L * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(end.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val out = mutableListOf<Event>()
        try {
            context.contentResolver.query(
                uri, projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC",
            )?.use { c ->
                val ti = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val bi = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val li = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val ai = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val ci = c.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val title = ti.takeIf { it >= 0 }?.let { c.getString(it) }?.takeIf { it.isNotBlank() }
                        ?: "Bez názvu"
                    val begin = bi.takeIf { it >= 0 }?.let { c.getLong(it) } ?: continue
                    val location = li.takeIf { it >= 0 }?.let { c.getString(it) }
                    val allDay = ai.takeIf { it >= 0 }?.let { c.getInt(it) == 1 } ?: false
                    val cal = ci.takeIf { it >= 0 }?.let { c.getString(it) }
                    out.add(Event(title, begin, location, allDay, cal))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Čtení kalendáře selhalo", e)
        }
        return out
    }

    /**
     * Z události udělá předvyplnění: má-li místo, zkusí ho geokódovat a udělat
     * z ní připomínku na místo; jinak (nebo když se místo nenajde) na čas.
     * Volat na IO vlákně.
     */
    fun toPrefill(context: Context, event: Event): Prefill {
        val dueDate = if (event.allDay) atNineLocal(event.begin) else event.begin
        val loc = event.location?.trim()?.takeIf { it.isNotBlank() }
        val coord = loc?.let { geocode(context, it) }
        return if (coord != null) {
            Prefill(event.title, dueDate, ReminderKind.LOCATION, loc, coord)
        } else {
            Prefill(event.title, dueDate, ReminderKind.TIME, "", null)
        }
    }

    /** Celodenní událost: začátek je půlnoc (UTC) daného dne → udělat z něj 9:00 místního času. */
    private fun atNineLocal(utcMidnight: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
        return Calendar.getInstance().apply {
            clear()
            set(
                utc.get(Calendar.YEAR),
                utc.get(Calendar.MONTH),
                utc.get(Calendar.DAY_OF_MONTH),
                9, 0, 0,
            )
        }.timeInMillis
    }

    @Suppress("DEPRECATION")
    private fun geocode(context: Context, address: String): LatLng? {
        return try {
            if (!Geocoder.isPresent()) return null
            val results = Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)
            results?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        } catch (e: Exception) {
            Log.w(TAG, "Geokódování adresy selhalo", e)
            null
        }
    }
}
