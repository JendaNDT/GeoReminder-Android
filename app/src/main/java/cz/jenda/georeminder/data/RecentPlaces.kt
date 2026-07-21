package cz.jenda.georeminder.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Naposledy vybraná místa ve výběru na mapě (max 5) – pro rychlé návrhy
 * při prázdném hledacím poli.
 */
object RecentPlaces {
    private const val PREFS = "georeminder"
    private const val KEY = "recentPlaces"
    private const val LIMIT = 5

    data class Entry(
        val name: String,
        val latitude: Double,
        val longitude: Double,
    )

    fun load(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name")
                val lat = obj.optDouble("lat")
                val lng = obj.optDouble("lng")
                if (name.isBlank() || lat.isNaN() || lng.isNaN()) null
                else Entry(name, lat, lng)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, name: String, latitude: Double, longitude: Double) {
        if (name.isBlank()) return
        val current = load(context).filterNot {
            // stejné místo (podle jména, nebo ~stejných souřadnic) nedublovat
            it.name.equals(name, ignoreCase = true) ||
                    (abs(it.latitude - latitude) < 0.0005 &&
                            abs(it.longitude - longitude) < 0.0005)
        }
        val updated = (listOf(Entry(name, latitude, longitude)) + current).take(LIMIT)
        val array = JSONArray()
        updated.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("lat", it.latitude)
                    .put("lng", it.longitude)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}
