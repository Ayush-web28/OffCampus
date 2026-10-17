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
 * Free, Uber-style "type and see place suggestions" search, backed by curated campus & local presets
 * and augmented by Photon (https://photon.komoot.io) OpenStreetMap geocoding.
 */
object PlaceSearchService {

    /**
     * Curated local presets for campus gates, nearby student spots, and major transit hubs.
     * Provides instantaneous zero-latency suggestions without network dependency or timeout risk.
     */
    val CAMPUS_PRESETS = listOf(
        PlaceSuggestion("Gate 1 (Main Gate)", "Main Campus, Vile Parle West", 19.1076, 72.8263),
        PlaceSuggestion("Gate 2 (Back Gate)", "Main Campus, Vile Parle West", 19.1072, 72.8268),
        PlaceSuggestion("Gate 3 (MPSTME / Engineering)", "Mukesh Patel School, JVPD Scheme", 19.1082, 72.8258),
        PlaceSuggestion("Gate 4 (Mithibai College)", "SVKM Campus, Vile Parle West", 19.1031, 72.8361),
        PlaceSuggestion("Campus Canteen", "Ground Floor, Main Campus", 19.1076, 72.8263),
        PlaceSuggestion("Central Library", "Main Campus, Vile Parle West", 19.1076, 72.8263),
        PlaceSuggestion("Boys Hostel", "JVPD Scheme, Vile Parle West", 19.1090, 72.8280),
        PlaceSuggestion("Girls Hostel", "JVPD Scheme, Vile Parle West", 19.1085, 72.8290),
        PlaceSuggestion("Devniwas Society", "Vile Parle West, Mumbai", 19.1050, 72.8310),
        PlaceSuggestion("Vile Parle Station (West)", "Station Road, Vile Parle West", 19.1002, 72.8436),
        PlaceSuggestion("Vile Parle Station (East)", "Subhash Road, Vile Parle East", 19.1010, 72.8450),
        PlaceSuggestion("Andheri Station (West)", "SV Road, Andheri West", 19.1197, 72.8464),
        PlaceSuggestion("Andheri Station (East)", "Station Road, Andheri East", 19.1200, 72.8480),
        PlaceSuggestion("Airport Terminal 1 (Domestic)", "Santacruz East, Mumbai", 19.0886, 72.8535),
        PlaceSuggestion("Airport Terminal 2 (International)", "Sahar, Andheri East, Mumbai", 19.0968, 72.8747),
        PlaceSuggestion("Bandra Kurla Complex (BKC)", "G Block, Bandra East", 19.0660, 72.8680),
        PlaceSuggestion("Bandra Station (West)", "Hill Road / SV Road, Bandra West", 19.0544, 72.8402),
        PlaceSuggestion("Juhu Beach", "Juhu Tara Road, Juhu", 19.0988, 72.8264),
        PlaceSuggestion("Infinity Mall (Andheri)", "Link Road, Andheri West", 19.1415, 72.8310),
        PlaceSuggestion("Phoenix Marketcity", "LBS Marg, Kurla West", 19.0864, 72.8890),
        PlaceSuggestion("Thane Station (West)", "Gokhale Road, Thane West", 19.1860, 72.9757),
        PlaceSuggestion("Thane Station (East)", "Station Road, Thane East", 19.1872, 72.9775),
        PlaceSuggestion("Borivali Station (West)", "SV Road, Borivali West", 19.2294, 72.8568),
        PlaceSuggestion("Dadar Station", "Dadar West, Mumbai", 19.0178, 72.8478)
    )

    /**
     * Instantly filters curated campus & transit presets matching the user's query.
     */
    fun searchLocalPresets(query: String): List<PlaceSuggestion> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return CAMPUS_PRESETS.filter { preset ->
            val name = preset.name.lowercase()
            val sub = preset.subtitle.lowercase()
            name.contains(q) || sub.contains(q) ||
                (q == "g1" && name.contains("gate 1")) ||
                (q == "g2" && name.contains("gate 2")) ||
                (q == "g3" && name.contains("gate 3")) ||
                (q == "g4" && name.contains("gate 4")) ||
                (q == "bkc" && (name.contains("bkc") || sub.contains("bkc"))) ||
                (q == "t1" && name.contains("terminal 1")) ||
                (q == "t2" && name.contains("terminal 2"))
        }
    }

    /** Opens and immediately discards a connection to Photon to pre-warm DNS & TLS. */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://photon.komoot.io/api/?q=a&limit=1").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("User-Agent", "OffCampus-Android/1.0 (college project)")
            }
            try {
                connection.responseCode
                connection.inputStream.close()
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            // Best-effort prewarm
        }
        Unit
    }

    /**
     * Returns matching suggestions by combining instantaneous local presets with
     * live OpenStreetMap Photon search (with a resilient 10s network timeout).
     */
    suspend fun search(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        val localMatches = searchLocalPresets(q)

        val remoteMatches = if (q.length >= 2) {
            try {
                val url = "https://photon.komoot.io/api/?q=${URLEncoder.encode(q, "UTF-8")}" +
                    "&lat=$CAMPUS_LAT&lon=$CAMPUS_LON&limit=5"
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("User-Agent", "OffCampus-Android/1.0 (college project)")
                }
                val body = try {
                    if (connection.responseCode !in 200..299) null
                    else connection.inputStream.bufferedReader().readText()
                } finally {
                    connection.disconnect()
                }
                if (body != null) parseFeatures(body) else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Merge local presets first, followed by remote results, deduplicating by normalized name prefix
        val seen = mutableSetOf<String>()
        val combined = mutableListOf<PlaceSuggestion>()
        for (item in localMatches) {
            val key = item.name.lowercase().take(12)
            if (seen.add(key)) combined.add(item)
        }
        for (item in remoteMatches) {
            val key = item.name.lowercase().take(12)
            if (seen.add(key)) combined.add(item)
        }
        combined.take(6)
    }

    private fun parseFeatures(body: String): List<PlaceSuggestion> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val feature = features.getJSONObject(i)
            val props = feature.optJSONObject("properties") ?: return@mapNotNull null
            val name = props.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
                ?: return@mapNotNull null
            val lng = coordinates.optDouble(0)
            val lat = coordinates.optDouble(1)

            val subtitle = listOf("street", "district", "city", "state")
                .mapNotNull { key -> props.optString(key).takeIf { it.isNotBlank() } }
                .distinct()
                .joinToString(", ")

            PlaceSuggestion(name = name, subtitle = subtitle, lat = lat, lng = lng)
        }
    }
}
