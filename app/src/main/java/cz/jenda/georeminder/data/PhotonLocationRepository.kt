package cz.jenda.georeminder.data

import android.location.Location
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Výsledek hledání z Photon API. */
data class PhotonItem(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val osmKey: String,
    val osmValue: String,
)

/** Izolovaná síťová vrstva pro vyhledávání míst přes Photon API (photon.komoot.io). */
object PhotonLocationRepository {

    suspend fun search(
        query: String,
        near: Location?,
    ): List<PhotonItem> = withContext(Dispatchers.IO) {
        val urlString = buildString {
            append("https://photon.komoot.io/api/?q=")
            append(URLEncoder.encode(query, "UTF-8"))
            append("&limit=5")
            if (near != null) {
                append("&lat=${near.latitude}&lon=${near.longitude}")
            }
        }
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty("User-Agent", "GeoReminder-Android")

        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                android.util.Log.w("PhotonRepo", "HTTP chyba $code při hledání místa")
                return@withContext emptyList()
            }
            val body = connection.inputStream.bufferedReader().readText()
            val features = JSONObject(body).optJSONArray("features") ?: return@withContext emptyList()
            val results = mutableListOf<PhotonItem>()

            for (i in 0 until minOf(features.length(), 5)) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: continue
                val coords = feature.optJSONObject("geometry")
                    ?.optJSONArray("coordinates") ?: continue
                val lng = coords.optDouble(0)
                val lat = coords.optDouble(1)
                if (lat.isNaN() || lng.isNaN()) continue

                val street = listOf(props.optString("street"), props.optString("housenumber"))
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val title = props.optString("name")
                    .ifBlank { street }
                    .ifBlank { props.optString("city") }
                    .ifBlank { "Bez názvu" }

                val subtitleParts = mutableListOf<String>()
                if (street.isNotBlank() && street != title) subtitleParts += street
                props.optString("city")
                    .takeIf { it.isNotBlank() && it != title }
                    ?.let { subtitleParts += it }
                props.optString("country")
                    .takeIf { it.isNotBlank() && it != "Česko" && it != "Czechia" }
                    ?.let { subtitleParts += it }

                results += PhotonItem(
                    title = title,
                    subtitle = subtitleParts.joinToString(", "),
                    latitude = lat,
                    longitude = lng,
                    osmKey = props.optString("osm_key"),
                    osmValue = props.optString("osm_value"),
                )
            }
            results
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("PhotonRepo", "Hledání v Photon API selhalo", e)
            // Volající rozliší výpadek služby od legitimního prázdného výsledku
            // a může po neúspěchu lokálního geokodéru ukázat offline stav.
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
