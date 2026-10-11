package io.github.duwenji.sanpoguide.data

import io.github.duwenji.sanpoguide.history.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A way to a spot. [points] run from the user to the spot; [onPaths] is false for the
 * straight line drawn when no route could be found.
 */
data class WalkRoute(val points: List<LatLon>, val distanceM: Double, val onPaths: Boolean) {
    companion object {
        fun straight(from: LatLon, to: LatLon) =
            WalkRoute(listOf(from, to), RouteGeometry.distanceM(from, to), onPaths = false)
    }
}

/**
 * Walking routes from the FOSSGIS OSRM server (OpenStreetMap data, no API key; a public
 * service for light use, so callers should ask only when the target or the user's track changes).
 */
class RouteClient(
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
) {
    suspend fun walk(from: LatLon, to: LatLon): WalkRoute = withContext(Dispatchers.IO) {
        // OSRM takes lon,lat; the "driving" segment is fixed by the API, the foot profile is in the host path.
        val url = String.format(
            Locale.ROOT, "%s/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
            ENDPOINT, from.lon, from.lat, to.lon, to.lat,
        )
        val request = Request.Builder().url(url).header("User-Agent", "SanpoGuide/0.1 (Android)").build()
        http.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw IOException("Routing error: HTTP ${res.code}")
            parse(res.body!!.string())
        }
    }

    internal fun parse(body: String): WalkRoute {
        val json = JSONObject(body)
        if (json.optString("code") != "Ok") throw IOException("Routing error: ${json.optString("code")}")
        val route = json.getJSONArray("routes").getJSONObject(0)
        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coords.length()).map { i ->
            coords.getJSONArray(i).let { LatLon(lat = it.getDouble(1), lon = it.getDouble(0)) }
        }
        if (points.size < 2) throw IOException("Routing error: empty route")
        return WalkRoute(points, route.optDouble("distance", 0.0), onPaths = true)
    }

    private companion object {
        const val ENDPOINT = "https://routing.openstreetmap.de/routed-foot/route/v1/driving"
    }
}

/** Plane approximations; good to a few meters over walking distances. */
object RouteGeometry {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceM(a: LatLon, b: LatLon): Double {
        val (x, y) = project(b, a.lat)
        val (x0, y0) = project(a, a.lat)
        return hypot(x - x0, y - y0)
    }

    /** How far [p] is from the nearest point of the line through [points]. */
    fun distanceToLineM(p: LatLon, points: List<LatLon>): Double {
        if (points.isEmpty()) return Double.MAX_VALUE
        val (px, py) = project(p, p.lat)
        if (points.size == 1) return distanceM(p, points[0])
        return points.zipWithNext().minOf { (a, b) ->
            val (ax, ay) = project(a, p.lat)
            val (bx, by) = project(b, p.lat)
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
            hypot(px - (ax + t * dx), py - (ay + t * dy))
        }
    }

    /** At most [maxPoints] (≥ 2) evenly spread points of [points], keeping the first and the last. */
    fun thin(points: List<LatLon>, maxPoints: Int): List<LatLon> {
        if (points.size <= maxPoints) return points.toList()
        val step = (points.size - 1).toDouble() / (maxPoints - 1)
        return List(maxPoints) { i -> points[(i * step).roundToInt().coerceAtMost(points.lastIndex)] }
    }

    private fun project(p: LatLon, refLat: Double): Pair<Double, Double> {
        val x = Math.toRadians(p.lon) * cos(Math.toRadians(refLat)) * EARTH_RADIUS_M
        val y = Math.toRadians(p.lat) * EARTH_RADIUS_M
        return x to y
    }
}
