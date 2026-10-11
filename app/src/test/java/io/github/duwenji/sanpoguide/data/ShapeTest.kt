package io.github.duwenji.sanpoguide.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShapeTest {
    // Roughly 111 m per 0.001° of latitude; at 35°N, 0.001° of longitude is about 91 m.
    private val lat0 = 35.0
    private val lon0 = 139.0

    /** A closed square ring 0.004° on a side, south-west corner at (lat0, lon0). */
    private fun square(minLat: Double = lat0, minLon: Double = lon0, size: Double = 0.004) = doubleArrayOf(
        minLat, minLon, minLat, minLon + size, minLat + size, minLon + size, minLat + size, minLon, minLat, minLon,
    )

    @Test
    fun insideAnAreaIsZero() {
        val park = Shape(listOf(square()), isArea = true)
        // Near a corner, far from the center: this is the case the center-point check missed.
        assertEquals(0.0, park.distanceM(lat0 + 0.0003, lon0 + 0.0003), 0.0)
        assertEquals(0.0, park.distanceM(lat0 + 0.002, lon0 + 0.002), 0.0)
    }

    @Test
    fun outsideMeasuresToTheNearestEdge() {
        val park = Shape(listOf(square()), isArea = true)
        // 0.0005° south of the southern edge: about 55 m.
        assertEquals(55.6, park.distanceM(lat0 - 0.0005, lon0 + 0.002), 1.0)
        // Diagonal from the south-west corner: hypot(55.6, 45.5).
        assertEquals(71.9, park.distanceM(lat0 - 0.0005, lon0 - 0.0005), 1.5)
    }

    @Test
    fun innerRingIsAHole() {
        val outer = square()
        val pond = square(lat0 + 0.001, lon0 + 0.001, 0.002)
        val park = Shape(listOf(outer, pond), isArea = true)
        // In the middle of the pond: 0.001° from its edge in latitude, ~91 m in longitude.
        assertEquals(91.0, park.distanceM(lat0 + 0.002, lon0 + 0.002), 1.5)
        // On the grass between the pond and the outer edge.
        assertEquals(0.0, park.distanceM(lat0 + 0.0005, lon0 + 0.002), 0.0)
    }

    @Test
    fun ringSplitAcrossWaysStillEnclosesTheArea() {
        val s = square()
        val park = Shape(listOf(s.copyOfRange(0, 6), s.copyOfRange(4, 10)), isArea = true)
        assertEquals(0.0, park.distanceM(lat0 + 0.002, lon0 + 0.002), 0.0)
    }

    @Test
    fun lineHasNoInside() {
        val wall = Shape(listOf(square()), isArea = false)
        // From the middle to the east/west side, 0.002° of longitude.
        assertEquals(182.2, wall.distanceM(lat0 + 0.002, lon0 + 0.002), 1.5)
    }
}
