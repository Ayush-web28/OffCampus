package com.offcampus.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One result from [PlaceSearchService.search] — a real place with coordinates, not just text,
 * so a lobby can eventually be matched by "is this place near that one" (see CLAUDE.md Fix 16). */
data class PlaceSuggestion(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double
)

// Roughly NMIMS Deemed University's main campus (Vile Parle West, Mumbai) — every search is
// biased toward here, since almost every trip in this app starts near it. Photon treats lat/lon
// as a ranking hint, not a hard filter, so a search for a genuinely distant place (someone's
// hometown destination, say) still returns real results, just not artificially prioritized.
private const val CAMPUS_LAT = 19.1076
private const val CAMPUS_LON = 72.8263

/**
 * Free, Uber-style "type and see place suggestions" search, backed by Photon
 * (https://photon.komoot.io) — a public geocoder built on OpenStreetMap data. Deliberately not
 * Google Places: that needs a billing-enabled Google Cloud project even to stay within its free
 * monthly credit, which this project has ruled out everywhere else (Cloud Functions, Storage).
 * Photon needs no API key and no billing at all. The tradeoff, worth remembering: OSM's data is
 * volunteer-maintained, so a very small or brand-new local landmark might not be mapped yet,
 * where Google's commercial data usually would have it.
 */
object PlaceSearchService {
    /** Opens and immediately discards a connection to Photon — meant to be fired the moment the
     * post-trip screen appears, well before the rider reaches the Gate or Destination field.
     * Testing found the slow part of a search is almost always the *first* request's DNS + TLS
     * handshake to a fresh host, not Photon's own lookup — this pays that cost in the background
     * while the rider is still filling in Checkpoint, so the connection is warm (pooled and
     * reusable) by the time they actually start typing a place name. Best-effort only: if it
     * fails or never finishes, the real search below still works on its own, just without this
     * head start. */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://photon.komoot.io/api/?q=a&limit=1").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("User-Agent", "OffCampus-Android/1.0 (college project)")
            }
            try {
                connection.responseCode // forces the connection to actually open
                connection.inputStream.close()
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            // Nothing to do — this is purely an optimization, not a dependency.
        }
        Unit
    }

    /** Empty list on no matches OR on any network/parsing failure — a search-as-you-type field
     * should never block the user from just typing the place name by hand instead. */
    suspend fun search(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url = "https://photon.komoot.io/api/?q=${URLEncoder.encode(query, "UTF-8")}" +
                "&lat=$CAMPUS_LAT&lon=$CAMPUS_LON&limit=5"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                // Tuned by hand against real trials, not guessed: 2-3s failed outright most of
                // the time even with warmUp() run first, since Photon's public server genuinely
                // takes that long on a fresh request. 4s paired with warmUp() is the shortest
                // value that held up across repeated real runs.
                connectTimeout = 4_000
                readTimeout = 4_000
                // Photon's public instance is a free shared resource — identifying the app is
                // considerate, not required, the same spirit as not hammering it every keystroke.
                setRequestProperty("User-Agent", "OffCampus-Android/1.0 (college project)")
            }
            val body = try {
                if (connection.responseCode !in 200..299) return@withContext emptyList()
                connection.inputStream.bufferedReader().readText()
            } finally {
                connection.disconnect()
            }
            parseFeatures(body)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseFeatures(body: String): List<PlaceSuggestion> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val feature = features.getJSONObject(i)
            val props = feature.optJSONObject("properties") ?: return@mapNotNull null
            val name = props.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
                ?: return@mapNotNull null
            // GeoJSON orders coordinates [lon, lat], the opposite of how they're usually spoken.
            val lng = coordinates.optDouble(0)
            val lat = coordinates.optDouble(1)

            // Everything below the place's own name — street, locality, city — joined into one
            // subtitle line, the same "name, then where it is" shape as Uber's own picker.
            val subtitle = listOf("street", "district", "city", "state")
                .mapNotNull { key -> props.optString(key).takeIf { it.isNotBlank() } }
                .distinct()
                .joinToString(", ")

            PlaceSuggestion(name = name, subtitle = subtitle, lat = lat, lng = lng)
        }
    }
}
