package cz.jenda.georeminder.data

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Rozluští polohu ze sdíleného textu – typicky „Sdílet" z Map Google
 * (krátký odkaz maps.app.goo.gl) nebo geo: souřadnice z jiných aplikací.
 * Vrací (název místa, souřadnice), nebo null, když se poloha najít nedá.
 */
object PlaceLinkResolver {

    suspend fun resolve(sharedText: String): Pair<String, LatLng>? =
        withContext(Dispatchers.IO) {
            try {
                resolveInternal(sharedText.trim().take(10_000))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    private fun resolveInternal(text: String): Pair<String, LatLng>? {
        // 1) geo: souřadnice (geo:50.1,14.4 nebo geo:0,0?q=50.1,14.4(Název))
        if (text.startsWith("geo:")) {
            return parseGeoUri(text)
        }

        // 2) Najít odkaz v textu
        val url = Regex("""https?://\S+""").find(text)?.value
            ?.trimEnd(')', '.', ',', ';') ?: return null
        if (!isAllowedGoogleMapsUrl(url)) return null

        // Krátké odkazy (maps.app.goo.gl apod.) rozbalit přes přesměrování
        var coords = parseCoordsFromUrl(url)
        var finalUrl = url
        if (coords == null) {
            finalUrl = expandRedirects(url)
            coords = parseCoordsFromUrl(finalUrl) ?: return null
        }

        val name = parseNameFromUrl(finalUrl)
            ?: text.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("http") }
                ?.take(60)
            ?: ""

        return name to coords
    }

    private fun parseGeoUri(uri: String): Pair<String, LatLng>? {
        // q=lat,lng(Label) má přednost (geo:0,0?q=… je běžný formát)
        val q = Regex("""[?&]q=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)(?:\((.+?)\))?""")
            .find(uri)
        if (q != null) {
            val lat = q.groupValues[1].toDoubleOrNull() ?: return null
            val lng = q.groupValues[2].toDoubleOrNull() ?: return null
            val label = q.groupValues[3].let {
                try {
                    URLDecoder.decode(it, "UTF-8")
                } catch (_: Exception) {
                    it
                }
            }
            if (!validCoordinates(lat, lng)) return null
            return label.take(200) to LatLng(lat, lng)
        }
        val m = Regex("""geo:(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""").find(uri) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lng = m.groupValues[2].toDoubleOrNull() ?: return null
        if (!validCoordinates(lat, lng) || lat == 0.0 && lng == 0.0) return null
        return "" to LatLng(lat, lng)
    }

    /** Následuje přesměrování krátkých odkazů (max 6 skoků). */
    private fun expandRedirects(startUrl: String): String {
        var current = startUrl
        repeat(6) {
            if (!isAllowedGoogleMapsUrl(current)) return startUrl
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "GeoReminder-Android")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return current
                    val next = if (location.startsWith("http")) {
                        location
                    } else {
                        URL(URL(current), location).toString()
                    }
                    if (!isAllowedGoogleMapsUrl(next)) return current
                    current = next
                } else {
                    return current
                }
            } finally {
                conn.disconnect()
            }
        }
        return current
    }

    private fun isAllowedGoogleMapsUrl(raw: String): Boolean {
        return try {
            val url = URL(raw)
            if (url.protocol != "https") {
                false
            } else {
                val host = url.host.lowercase().trimEnd('.')
                val isGoogleDomain = host == "google.com" ||
                    host.endsWith(".google.com") ||
                    Regex("(^|\\.)google\\.[a-z.]{2,12}$").matches(host)
                host == "maps.app.goo.gl" ||
                    (host == "goo.gl" && url.path.startsWith("/maps")) ||
                    (isGoogleDomain && (host.startsWith("maps.") || url.path.startsWith("/maps")))
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    /** Vytáhne souřadnice z odkazu na Mapy Google. */
    private fun parseCoordsFromUrl(url: String): LatLng? {
        // !3d…!4d… = souřadnice špendlíku (nejpřesnější)
        Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""").find(url)?.let {
            val lat = it.groupValues[1].toDouble()
            val lng = it.groupValues[2].toDouble()
            return if (validCoordinates(lat, lng)) LatLng(lat, lng) else null
        }
        // ?q=lat,lng nebo &ll=lat,lng
        Regex("""[?&](?:q|ll|query)=(-?\d+\.\d+),(-?\d+\.\d+)""").find(url)?.let {
            val lat = it.groupValues[1].toDouble()
            val lng = it.groupValues[2].toDouble()
            return if (validCoordinates(lat, lng)) LatLng(lat, lng) else null
        }
        // /@lat,lng,zoom = střed mapy (záloha)
        Regex("""/@(-?\d+\.\d+),(-?\d+\.\d+)""").find(url)?.let {
            val lat = it.groupValues[1].toDouble()
            val lng = it.groupValues[2].toDouble()
            return if (validCoordinates(lat, lng)) LatLng(lat, lng) else null
        }
        return null
    }

    /** Název místa z části „/place/Název+Místa/" v odkazu. */
    private fun parseNameFromUrl(url: String): String? {
        val m = Regex("""/place/([^/@?]+)""").find(url) ?: return null
        val raw = m.groupValues[1].replace('+', ' ')
        val decoded = try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
        return decoded.trim().take(200).takeIf { it.isNotBlank() }
    }
}
