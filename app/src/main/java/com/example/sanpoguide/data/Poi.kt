package com.example.sanpoguide.data

import android.location.Location

data class Poi(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val tags: Map<String, String>,
) {
    /** Rough "worth talking about" score: well-documented places and major landmarks first. */
    val notability: Int
        get() = (if ("wikipedia" in tags || "wikidata" in tags) 2 else 0) +
            (if (category in MAJOR_CATEGORIES) 1 else 0)

    fun distanceFrom(location: Location): Float {
        val result = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, lat, lon, result)
        return result[0]
    }

    private companion object {
        val MAJOR_CATEGORIES = setOf("神社", "寺院", "教会", "博物館", "名所", "展望スポット", "庭園")
    }
}
