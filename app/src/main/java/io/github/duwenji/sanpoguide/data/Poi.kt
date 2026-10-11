package io.github.duwenji.sanpoguide.data

import android.location.Location

data class Poi(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val tags: Map<String, String>,
    /** Outline for spots mapped as areas or lines; null for points. [lat]/[lon] is then its center. */
    val shape: Shape? = null,
) {
    /** Rough "worth talking about" score: well-documented places and major landmarks first. */
    val notability: Int
        get() = (if ("wikipedia" in tags || "wikidata" in tags) 2 else 0) +
            (if (category in MAJOR_CATEGORIES) 1 else 0)

    /** Distance in meters to the spot's edge (0 when inside an area), or to its point. */
    fun distanceFrom(location: Location): Float {
        shape?.let { return it.distanceM(location.latitude, location.longitude).toFloat() }
        val result = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, lat, lon, result)
        return result[0]
    }

    /** Whether a distance from [distanceFrom] means the user is inside this spot's area. */
    fun isInside(distanceM: Number): Boolean = shape?.isArea == true && distanceM.toDouble() == 0.0

    private companion object {
        val MAJOR_CATEGORIES = setOf("神社", "寺院", "教会", "博物館", "名所", "展望スポット", "庭園")
    }
}
