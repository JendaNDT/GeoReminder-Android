package cz.jenda.georeminder.data

import android.location.Location
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Locale

data class PhotonItem(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val osmKey: String,
    val osmValue: String,
)

sealed interface PhotonSearchResult {
    data class Success(val results: List<PhotonItem>) : PhotonSearchResult
    data object NoResults : PhotonSearchResult
    data object NetworkError : PhotonSearchResult
    data class ServerError(val code: Int) : PhotonSearchResult
    data object ParseError : PhotonSearchResult
}

object PhotonLocationRepository {

    suspend fun search(
        query: String,
        near: Location?,
    ): PhotonSearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext PhotonSearchResult.NoResults

        val urlString = buildString {
            append("https://photon.komoot.io/api/?q=")
            append(URLEncoder.encode(trimmed, "UTF-8"))
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
                return@withContext PhotonSearchResult.ServerError(code)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val features = try {
                JSONObject(body).getJSONArray("features")
            } catch (_: JSONException) {
                return@withContext PhotonSearchResult.ParseError
            }

            val results = mutableListOf<PhotonItem>()
            for (i in 0 until minOf(features.length(), 5)) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: continue
                val coords = feature.optJSONObject("geometry")
                    ?.optJSONArray("coordinates") ?: continue
                val lng = coords.optDouble(0)
                val lat = coords.optDouble(1)
                if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                    continue
                }

                val street = listOf(props.optString("street"), props.optString("housenumber"))
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val title = props.optString("name")
                    .ifBlank { street }
                    .ifBlank { props.optString("city") }
                    .ifBlank { String.format(Locale.ROOT, "%.5f, %.5f", lat, lng) }

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

            if (results.isEmpty()) PhotonSearchResult.NoResults
            else PhotonSearchResult.Success(results)
        } catch (e: CancellationException) {
            throw e
        } catch (_: UnknownHostException) {
            PhotonSearchResult.NetworkError
        } catch (_: SocketTimeoutException) {
            PhotonSearchResult.NetworkError
        } catch (_: IOException) {
            PhotonSearchResult.NetworkError
        } catch (_: JSONException) {
            PhotonSearchResult.ParseError
        } catch (e: Exception) {
            android.util.Log.w("PhotonRepo", "Unexpected Photon API error", e)
            PhotonSearchResult.ParseError
        } finally {
            connection.disconnect()
        }
    }
}
