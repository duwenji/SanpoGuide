package com.example.sanpoguide.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Searches OpenStreetMap (Overpass API) for walk-worthy spots around a point. */
class OverpassClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(40, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun search(lat: Double, lon: Double, radiusM: Int = 600): List<Poi> =
        withContext(Dispatchers.IO) {
            val around = "around:$radiusM,$lat,$lon"
            val query = """
                [out:json][timeout:25];
                (
                  nwr($around)["name"]["historic"];
                  nwr($around)["name"]["tourism"~"^(attraction|museum|viewpoint|artwork|gallery)$"];
                  nwr($around)["name"]["amenity"="place_of_worship"];
                  nwr($around)["name"]["leisure"~"^(park|garden)$"];
                  nwr($around)["name"]["natural"~"^(tree|peak|spring|water)$"];
                );
                out center tags 80;
            """.trimIndent()

            // Public Overpass servers are often busy (429/504); fall through to the next mirror.
            var lastError: IOException? = null
            for (endpoint in ENDPOINTS) {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "SanpoGuide/0.1 (Android)")
                    .post(FormBody.Builder().add("data", query).build())
                    .build()
                try {
                    http.newCall(request).execute().use { res ->
                        if (res.isSuccessful) return@withContext parse(res.body!!.string())
                        lastError = IOException("Overpass API error: HTTP ${res.code}")
                    }
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw lastError!!
        }

    private fun parse(json: String): List<Poi> {
        val elements = JSONObject(json).getJSONArray("elements")
        val result = LinkedHashMap<String, Poi>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            val tagsJson = e.optJSONObject("tags") ?: continue
            val tags = tagsJson.keys().asSequence().associateWith { tagsJson.getString(it) }
            val name = tags["name:ja"] ?: tags["name"] ?: continue
            val (lat, lon) = if (e.has("lat")) {
                e.getDouble("lat") to e.getDouble("lon")
            } else {
                val c = e.optJSONObject("center") ?: continue
                c.getDouble("lat") to c.getDouble("lon")
            }
            val id = "${e.getString("type")}/${e.getLong("id")}"
            // The same place is often mapped both as a node and an area; keep the first.
            val key = "$name@${"%.3f".format(lat)},${"%.3f".format(lon)}"
            result.putIfAbsent(key, Poi(id, name, lat, lon, categoryOf(tags), tags))
        }
        return result.values.toList()
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
        tags.containsKey("natural") -> "自然"
        else -> "スポット"
    }

    companion object {
        private val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        )
    }
}
