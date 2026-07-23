package cz.jenda.georeminder.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Naposledy vybraná místa ve výběru na mapě (max 5) – pro rychlé návrhy
 * při prázdném hledacím poli.
 */
object RecentPlaces {
    private const val KEY = "recentPlaces"
    private const val LIMIT = 5

    data class Entry(
        val name: String,
        val latitude: Double,
        val longitude: Double,
    )

    fun load(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name")
                val lat = obj.optDouble("lat")
                val lng = obj.optDouble("lng")
                if (name.isBlank() || !lat.isFinite() || !lng.isFinite() ||
                    lat !in -90.0..90.0 || lng !in -180.0..180.0
                ) null
                else Entry(name.take(200), lat, lng)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, name: String, latitude: Double, longitude: Double) {
        if (name.isBlank() || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return
        val cleanName = name.trim().take(200)
        val current = load(context).filterNot {
            // stejné místo (podle jména, nebo ~stejných souřadnic) nedublovat
            it.name.equals(cleanName, ignoreCase = true) ||
                    (abs(it.latitude - latitude) < 0.0005 &&
                            abs(it.longitude - longitude) < 0.0005)
        }
        val updated = (listOf(Entry(cleanName, latitude, longitude)) + current).take(LIMIT)
        val array = JSONArray()
        updated.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("lat", it.latitude)
                    .put("lng", it.longitude)
            )
        }
        context.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY, array.toString())
        }
    }
}
