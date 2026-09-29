package com.example.sanpoguide.companion

import com.example.sanpoguide.data.Facility
import com.example.sanpoguide.data.FacilityKind
import com.example.sanpoguide.settings.Threshold
import com.example.sanpoguide.settings.Thresholds

/**
 * Why a facility is worth mentioning, most urgent first. Amenities are only brought up when
 * the situation calls for them; otherwise every walk past a vending machine would be announced.
 * The limits are user settings ([Threshold]).
 */
enum class FacilityNeed(
    val kinds: Set<FacilityKind>,
    /** Mention only facilities this close. */
    val radius: Threshold,
    /** Minimum gap between two mentions for this need. */
    val repeat: Threshold,
    /** Only after walking this long; null for any time. */
    val after: Threshold?,
) {
    SHELTER(setOf(FacilityKind.SHELTER), Threshold.SHELTER_RADIUS_M, Threshold.SHELTER_REPEAT_MIN, null),
    TOILET(setOf(FacilityKind.TOILETS), Threshold.TOILET_RADIUS_M, Threshold.TOILET_REPEAT_MIN, Threshold.TOILET_AFTER_MIN),
    DRINK(
        setOf(FacilityKind.DRINKING_WATER, FacilityKind.VENDING_MACHINE),
        Threshold.DRINK_RADIUS_M, Threshold.DRINK_REPEAT_MIN, Threshold.DRINK_AFTER_MIN,
    ),
    SEAT(setOf(FacilityKind.BENCH, FacilityKind.SHELTER), Threshold.SEAT_RADIUS_M, Threshold.SEAT_REPEAT_MIN, Threshold.SEAT_AFTER_MIN),
}

/** [forecast]: a shelter picked because rain or snow is on the way, not falling yet. */
data class FacilityAdvice(val facility: Facility, val need: FacilityNeed, val distanceM: Int, val forecast: Boolean = false)

/** Decides whether a nearby facility should be mentioned now. Pure logic, no Android calls. */
object FacilityAdvisor {
    /**
     * @param nearby facilities with their distance from the user
     * @param mentioned ids of facilities already mentioned on this walk
     * @param lastMentionAt when each need was last mentioned on this walk
     */
    fun pick(
        nearby: List<Pair<Facility, Float>>,
        elapsedMs: Long,
        weather: Weather?,
        mentioned: Set<String>,
        lastMentionAt: Map<FacilityNeed, Long>,
        now: Long,
        limits: Thresholds = Thresholds(),
    ): FacilityAdvice? {
        for (need in FacilityNeed.entries) {
            if (!applies(need, elapsedMs, weather, now, limits)) continue
            val last = lastMentionAt[need]
            if (last != null && now - last < limits.ms(need.repeat)) continue
            val (facility, distance) = nearby
                .filter { (f, d) -> f.kind in need.kinds && d <= limits[need.radius] && f.id !in mentioned }
                .minByOrNull { it.second } ?: continue
            val forecast = need == FacilityNeed.SHELTER && weather?.isWet != true
            return FacilityAdvice(facility, need, distance.toInt(), forecast)
        }
        return null
    }

    private fun applies(need: FacilityNeed, elapsedMs: Long, weather: Weather?, now: Long, limits: Thresholds): Boolean {
        if (need.after != null && elapsedMs < limits.ms(need.after)) return false
        return when (need) {
            // Also ahead of rain, while there's still time to reach a roof.
            FacilityNeed.SHELTER -> weather != null && (weather.isWet || WeatherChangeDetector.detect(weather, now, limits) != null)
            FacilityNeed.DRINK -> (weather?.temperatureC ?: Double.NEGATIVE_INFINITY) >= limits[Threshold.DRINK_HOT_C]
            FacilityNeed.TOILET, FacilityNeed.SEAT -> true
        }
    }

    /**
     * Where [bearing] (to the facility) lies relative to the walking [heading], both in degrees
     * clockwise from north: "前方", "右手", "左手" or "後ろ".
     */
    fun relativeDirection(heading: Float, bearing: Float): String {
        val diff = ((bearing - heading) % 360 + 540) % 360 - 180 // -180..180, positive = right
        return when {
            diff in -45f..45f -> "前方"
            diff in 45f..135f -> "右手"
            diff in -135f..-45f -> "左手"
            else -> "後ろ"
        }
    }
}
