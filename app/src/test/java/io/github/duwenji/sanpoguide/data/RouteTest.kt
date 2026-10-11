package io.github.duwenji.sanpoguide.data

import io.github.duwenji.sanpoguide.history.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    // Roughly 111 m per 0.001° of latitude; at 35°N, 0.001° of longitude is about 91 m.
    private val a = LatLon(35.0, 139.0)
    private val b = LatLon(35.0, 139.002)

    @Test
    fun distanceMatchesTheRoughScale() {
        assertEquals(182.2, RouteGeometry.distanceM(a, b), 1.5)
        assertEquals(111.2, RouteGeometry.distanceM(a, LatLon(35.001, 139.0)), 1.0)
    }

    @Test
    fun distanceToLineIsToTheNearestSegment() {
        val line = listOf(a, b, LatLon(35.002, 139.002))
        // Beside the middle of the first segment, 0.0003° (about 33 m) north of it.
        assertEquals(33.4, RouteGeometry.distanceToLineM(LatLon(35.0003, 139.001), line), 1.0)
        // Past the end: measured to the end point, not to the segment's extension.
        assertEquals(111.2, RouteGeometry.distanceToLineM(LatLon(35.003, 139.002), line), 1.5)
        assertEquals(0.0, RouteGeometry.distanceToLineM(b, line), 0.01)
    }

    @Test
    fun thinKeepsBothEndsAndTheCount() {
        val points = List(101) { LatLon(35.0 + it * 0.0001, 139.0) }
        val thin = RouteGeometry.thin(points, 20)
        assertEquals(20, thin.size)
        assertEquals(points.first(), thin.first())
        assertEquals(points.last(), thin.last())
        assertEquals(points.take(5), RouteGeometry.thin(points.take(5), 20))
    }

    @Test
    fun parsesOsrmCoordinatesAsLonLat() {
        val body = """
            {"code":"Ok","routes":[{"distance":245.3,"geometry":{"type":"LineString",
             "coordinates":[[139.55050,35.31940],[139.55340,35.32270]]}}]}
        """.trimIndent()
        val route = RouteClient().parse(body)
        assertTrue(route.onPaths)
        assertEquals(245.3, route.distanceM, 0.01)
        assertEquals(LatLon(35.31940, 139.55050), route.points.first())
        assertEquals(LatLon(35.32270, 139.55340), route.points.last())
    }

    @Test
    fun noRouteIsAnError() {
        val failed = runCatching { RouteClient().parse("""{"code":"NoRoute","routes":[]}""") }
        assertTrue(failed.isFailure)
        assertFalse(WalkRoute.straight(a, b).onPaths)
    }
}
