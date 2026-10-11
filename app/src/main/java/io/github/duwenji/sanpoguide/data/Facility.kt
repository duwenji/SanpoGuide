package io.github.duwenji.sanpoguide.data

import android.location.Location

/** Walk amenities shown on the map; not spots to talk about. */
enum class FacilityKind(val label: String, val amenity: String) {
    TOILETS("トイレ", "toilets"),
    DRINKING_WATER("水飲み場", "drinking_water"),
    VENDING_MACHINE("自動販売機", "vending_machine"),
    SHELTER("休憩所", "shelter"),
    BENCH("ベンチ", "bench");

    companion object {
        fun of(amenity: String?): FacilityKind? = entries.firstOrNull { it.amenity == amenity }
    }
}

data class Facility(val id: String, val kind: FacilityKind, val lat: Double, val lon: Double, val name: String?) {
    fun distanceFrom(location: Location): Float = distanceFrom(location.latitude, location.longitude)

    fun distanceFrom(fromLat: Double, fromLon: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(fromLat, fromLon, lat, lon, result)
        return result[0]
    }
}
