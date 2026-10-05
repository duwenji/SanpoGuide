package com.example.sanpoguide.data

import android.util.Log
import com.example.sanpoguide.station.format.SpotKind
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
    /** [shops]: eating and shopping kinds to add (`SpotKind.onlyWhenPreferred`); none by default. */
    suspend fun search(lat: Double, lon: Double, radiusM: Int = 600, shops: Set<SpotKind> = emptySet()): NearbyResult =
        withContext(Dispatchers.IO) {
            val query = query(lat, lon, radiusM, shops)

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

    internal fun query(lat: Double, lon: Double, radiusM: Int, shops: Set<SpotKind>): String {
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

        return if (shops.isEmpty()) query else query + "\n" + shopQuery(lat, lon, radiusM, shops)
    }

    /**
     * Named places to eat and shop, without chains: convenience stores and supermarkets aren't
     * asked for, and anything OSM marks with a brand is left out. Searched closer than the other
     * spots, since a busy street has hundreds.
     */
    private fun shopQuery(lat: Double, lon: Double, radiusM: Int, kinds: Set<SpotKind>): String {
        val around = "around:${minOf(radiusM, SHOP_RADIUS_M)},$lat,$lon"
        val noChain = """[!"brand"][!"brand:wikidata"]"""
        val lines = kinds.mapNotNull { SHOP_FILTERS[it] }.joinToString("\n") { "  nwr($around)[\"name\"]$it$noChain;" }
        return "(\n$lines\n);\nout tags center $SHOP_LIMIT;"
    }

    internal fun parse(json: String): NearbyResult {
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
            val center = e.optJSONObject("center")
            val (lat, lon) = when {
                e.has("lat") -> e.getDouble("lat") to e.getDouble("lon")
                // Shops come with `out center` (no outline needed); the other spots with `out geom`.
                center != null -> center.getDouble("lat") to center.getDouble("lon")
                else -> {
                    val b = e.optJSONObject("bounds") ?: continue
                    (b.getDouble("minlat") + b.getDouble("maxlat")) / 2 to (b.getDouble("minlon") + b.getDouble("maxlon")) / 2
                }
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
        else -> shopKindOf(tags)?.category ?: "スポット"
    }

    private fun shopKindOf(tags: Map<String, String>): SpotKind? = when {
        tags["amenity"] == "restaurant" -> SpotKind.RESTAURANT
        tags["amenity"] == "cafe" || tags["amenity"] == "ice_cream" -> SpotKind.CAFE
        tags["amenity"] == "marketplace" -> SpotKind.MARKET
        else -> SHOP_TAGS.entries.firstOrNull { (_, values) -> tags["shop"] in values }?.key
    }

    private fun elapsed(since: Long) = System.currentTimeMillis() - since

    companion object {
        private const val TAG = "Overpass"
        private const val CLOSE_RADIUS_M = 250
        private const val SAME_FACILITY_M = 20f
        private const val SHOP_RADIUS_M = 400
        private const val SHOP_LIMIT = 60

        /** The `shop=*` values of each kind; chains' kinds (convenience, supermarket, ...) are in none. */
        internal val SHOP_TAGS: Map<SpotKind, Set<String>> = mapOf(
            SpotKind.SWEETS to setOf("confectionery", "pastry", "bakery", "chocolate"),
            SpotKind.FOOD_SHOP to setOf(
                "deli", "tea", "coffee", "alcohol", "wine", "seafood", "butcher", "greengrocer",
                "cheese", "spices", "tofu", "rice",
            ),
            SpotKind.CRAFTS to setOf("craft", "gift", "antiques", "art"),
            SpotKind.SHOP to setOf(
                "books", "second_hand", "stationery", "fabric", "clothes", "shoes", "bag", "jewelry",
                "leather", "musical_instrument", "music", "toys", "florist", "kitchen", "interior_decoration",
            ),
        )

        /** The Overpass filter for each eating and shopping kind. */
        internal val SHOP_FILTERS: Map<SpotKind, String> = mapOf(
            SpotKind.RESTAURANT to """["amenity"="restaurant"]""",
            SpotKind.CAFE to """["amenity"~"^(cafe|ice_cream)$"]""",
            SpotKind.MARKET to """["amenity"="marketplace"]""",
        ) + SHOP_TAGS.mapValues { (_, values) -> """["shop"~"^(${values.joinToString("|")})$"]""" }
        private val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        )
    }
}
