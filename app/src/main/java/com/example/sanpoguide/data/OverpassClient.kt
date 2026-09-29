package com.example.sanpoguide.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What one search found: spots to guide, and amenities for the map. */
data class NearbyResult(val spots: List<Poi>, val facilities: List<Facility>)

/** Searches OpenStreetMap (Overpass API) for walk-worthy spots and walk amenities around a point. */
class OverpassClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(40, TimeUnit.SECONDS)
        // The server may think for up to the query's [timeout:25] before sending anything;
        // OkHttp's default 10-second read timeout gave up on answers that were on their way.
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun search(lat: Double, lon: Double, radiusM: Int = 600): NearbyResult =
        withContext(Dispatchers.IO) {
            val around = "around:$radiusM,$lat,$lon"
            // Benches and vending machines are everywhere; only the close ones matter.
            val closeAround = "around:${minOf(radiusM, CLOSE_RADIUS_M)},$lat,$lon"
            val open = """["access"!~"^(private|no)$"]"""
            // Machines that sell nothing a walker needs (tickets, parking, cigarettes, ...).
            val usefulVending = """["vending"!~"ticket|parking|cigarette|excrement|condom|newspaper|fuel|photo"]"""
            val query = """
                [out:json][timeout:25];
                (
                  nwr($around)["name"]["historic"];
                  nwr($around)["name"]["tourism"~"^(attraction|museum|viewpoint|artwork|gallery)$"];
                  nwr($around)["name"]["amenity"="place_of_worship"];
                  nwr($around)["name"]["leisure"~"^(park|garden)$"];
                  nwr($around)["name"]["natural"~"^(tree|peak|spring|water|beach)$"];
                );
                out tags geom 80;
                nwr($around)["amenity"="toilets"]$open;
                out center 40;
                nwr($around)["amenity"="drinking_water"]$open;
                out center 40;
                nwr($around)["amenity"="shelter"]$open;
                out center 40;
                nwr($closeAround)["amenity"="bench"];
                out center 40;
                nwr($closeAround)["amenity"="vending_machine"]$usefulVending;
                out center 40;
            """.trimIndent()

            // Public Overpass servers are often busy (429/504); fall through to the next mirror.
            var lastError: IOException? = null
            for (endpoint in ENDPOINTS) {
                // Logged to measure how long searches take and how often a server gives up.
                val startedAt = System.currentTimeMillis()
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "SanpoGuide/0.1 (Android)")
                    .post(FormBody.Builder().add("data", query).build())
                    .build()
                try {
                    http.newCall(request).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body!!.string()
                            Log.i(TAG, "OK ${elapsed(startedAt)}ms ${body.length}B $endpoint")
                            return@withContext parse(body)
                        }
                        lastError = IOException("Overpass API error: HTTP ${res.code}")
                        Log.w(TAG, "HTTP ${res.code} after ${elapsed(startedAt)}ms $endpoint")
                    }
                } catch (e: IOException) {
                    lastError = e
                    Log.w(TAG, "${e.javaClass.simpleName} after ${elapsed(startedAt)}ms $endpoint")
                }
            }
            throw lastError!!
        }

    private fun parse(json: String): NearbyResult {
        val elements = JSONObject(json).getJSONArray("elements")
        val result = LinkedHashMap<String, Poi>()
        val facilities = mutableListOf<Facility>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            val tagsJson = e.optJSONObject("tags") ?: continue
            val tags = tagsJson.keys().asSequence().associateWith { tagsJson.getString(it) }
            FacilityKind.of(tags["amenity"])?.let { kind ->
                facilityOf(e, kind, tags)?.let { f ->
                    // A toilet is often mapped both as a node and its building; keep one.
                    if (facilities.none { it.kind == kind && it.distanceFrom(f.lat, f.lon) < SAME_FACILITY_M }) facilities += f
                }
                continue
            }
            val name = tags["name:ja"] ?: tags["name"] ?: continue
            val (lat, lon) = if (e.has("lat")) {
                e.getDouble("lat") to e.getDouble("lon")
            } else {
                val b = e.optJSONObject("bounds") ?: continue
                (b.getDouble("minlat") + b.getDouble("maxlat")) / 2 to (b.getDouble("minlon") + b.getDouble("maxlon")) / 2
            }
            val id = "${e.getString("type")}/${e.getLong("id")}"
            val poi = Poi(id, name, lat, lon, categoryOf(tags), tags, shapeOf(e, tags))
            // The same place is often mapped both as a node and an area. Keep the area, whose
            // edge tells when the user arrives, but also the node's tags (often the wikipedia link).
            val key = "$name@${"%.3f".format(lat)},${"%.3f".format(lon)}"
            val seen = result[key]
            result[key] = when {
                seen == null -> poi
                seen.shape == null && poi.shape != null -> poi.copy(tags = seen.tags + poi.tags)
                else -> seen
            }
        }
        return NearbyResult(result.values.toList(), facilities)
    }

    /** Nodes carry their position; ways and relations get a `center` from `out center`. */
    private fun facilityOf(e: JSONObject, kind: FacilityKind, tags: Map<String, String>): Facility? {
        val point = if (e.has("lat")) e else e.optJSONObject("center") ?: return null
        val name = tags["name:ja"] ?: tags["name"]
        return Facility(
            "${e.getString("type")}/${e.getLong("id")}", kind,
            point.getDouble("lat"), point.getDouble("lon"),
            // Bus stop roofs keep the rain off but aren't rest houses; say what they are.
            if (kind == FacilityKind.SHELTER && tags["shelter_type"] == "public_transport") name ?: "バス停" else name,
        )
    }

    /** The outline of a way or relation, from the geometry `out geom` adds; null for nodes. */
    private fun shapeOf(e: JSONObject, tags: Map<String, String>): Shape? {
        val type = e.getString("type")
        val lines = when (type) {
            "way" -> listOfNotNull(e.optJSONArray("geometry")?.let(::coords))
            "relation" -> {
                val members = e.optJSONArray("members") ?: JSONArray()
                (0 until members.length()).map(members::getJSONObject)
                    .filter { it.optString("type") == "way" }
                    .mapNotNull { it.optJSONArray("geometry")?.let(::coords) }
            }
            else -> emptyList()
        }.filter { it.size >= 4 }
        if (lines.isEmpty()) return null
        val isArea = tags["area"] != "no" && when (type) {
            // A closed way is an area (a park); an open one is a line (a wall, an old road).
            "way" -> lines[0].let { it[0] == it[it.size - 2] && it[1] == it[it.size - 1] }
            else -> tags["type"] == "multipolygon" || tags["type"] == "boundary"
        }
        return Shape(lines, isArea)
    }

    private fun coords(geometry: JSONArray): DoubleArray {
        val points = (0 until geometry.length()).mapNotNull(geometry::optJSONObject)
        return DoubleArray(points.size * 2) { i ->
            points[i / 2].getDouble(if (i % 2 == 0) "lat" else "lon")
        }
    }

    private fun categoryOf(tags: Map<String, String>): String = when {
        tags["amenity"] == "place_of_worship" -> when (tags["religion"]) {
            "shinto" -> "神社"
            "buddhist" -> "寺院"
            "christian" -> "教会"
            else -> "礼拝所"
        }
        tags.containsKey("historic") -> "史跡"
        tags["tourism"] == "museum" -> "博物館"
        tags["tourism"] == "gallery" -> "ギャラリー"
        tags["tourism"] == "viewpoint" -> "展望スポット"
        tags["tourism"] == "artwork" -> "アート"
        tags["tourism"] == "attraction" -> "名所"
        tags["leisure"] == "park" -> "公園"
        tags["leisure"] == "garden" -> "庭園"
        tags["natural"] == "beach" -> "海辺"
        tags["natural"] == "water" -> "水辺"
        tags.containsKey("natural") -> "自然"
        else -> "スポット"
    }

    private fun elapsed(since: Long) = System.currentTimeMillis() - since

    companion object {
        private const val TAG = "Overpass"
        private const val CLOSE_RADIUS_M = 250
        private const val SAME_FACILITY_M = 20f
        private val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        )
    }
}
