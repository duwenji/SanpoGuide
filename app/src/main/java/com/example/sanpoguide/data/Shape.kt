package com.example.sanpoguide.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/**
 * The outline of a spot mapped as a way or relation (a park's boundary, a castle wall...), so
 * distance can be measured to its edge rather than to its center.
 *
 * @param lines each line as `[lat0, lon0, lat1, lon1, ...]`. For an area these are its rings (or,
 *   for a multipolygon, the pieces of its rings, in any order); inner rings make holes.
 * @param isArea whether the lines enclose an area the user can be inside of
 */
class Shape(val lines: List<DoubleArray>, val isArea: Boolean) {

    /** Distance in meters from the point to the shape: 0 inside an area, else to the nearest edge. */
    fun distanceM(lat: Double, lon: Double): Double {
        // Project onto a local plane around the point; accurate enough at walking distances.
        val mPerLat = EARTH_RADIUS_M * PI / 180
        val mPerLon = mPerLat * cos(lat * PI / 180)
        var nearest = Double.MAX_VALUE
        var crossings = 0
        for (line in lines) {
            var i = 0
            while (i + 3 < line.size) {
                val y1 = (line[i] - lat) * mPerLat
                val x1 = (line[i + 1] - lon) * mPerLon
                val y2 = (line[i + 2] - lat) * mPerLat
                val x2 = (line[i + 3] - lon) * mPerLon
                nearest = minOf(nearest, segmentDistance(x1, y1, x2, y2))
                // Even-odd rule: count edges crossing the ray from the point towards +x.
                if ((y1 > 0) != (y2 > 0) && x1 + (0 - y1) * (x2 - x1) / (y2 - y1) > 0) crossings++
                i += 2
            }
        }
        return if (isArea && crossings % 2 == 1) 0.0 else nearest
    }

    /** Distance from the origin to the segment (x1, y1)-(x2, y2). */
    private fun segmentDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-x1 * dx - y1 * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(x1 + t * dx, y1 + t * dy)
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_008.8
    }
}
