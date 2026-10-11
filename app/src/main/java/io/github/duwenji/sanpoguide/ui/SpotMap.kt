package io.github.duwenji.sanpoguide.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.duwenji.sanpoguide.R
import io.github.duwenji.sanpoguide.data.Facility
import io.github.duwenji.sanpoguide.data.FacilityKind
import io.github.duwenji.sanpoguide.history.LatLon
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

/** Marker for each facility kind; the colors double as the legend in the nearest-facilities bar. */
fun facilityDot(kind: FacilityKind) = when (kind) {
    FacilityKind.TOILETS -> R.drawable.facility_toilet_dot
    FacilityKind.DRINKING_WATER -> R.drawable.facility_water_dot
    FacilityKind.VENDING_MACHINE -> R.drawable.facility_vending_dot
    FacilityKind.SHELTER -> R.drawable.facility_shelter_dot
    FacilityKind.BENCH -> R.drawable.facility_bench_dot
}

/**
 * The fill of each [facilityDot], for Compose: painterResource can't load `<shape>` drawables.
 * Keep the two in step.
 */
fun facilityColor(kind: FacilityKind) = when (kind) {
    FacilityKind.TOILETS -> Color(0xFF1565C0)
    FacilityKind.DRINKING_WATER -> Color(0xFF00838F)
    FacilityKind.VENDING_MACHINE -> Color(0xFFEF6C00)
    FacilityKind.SHELTER -> Color(0xFF6A1B9A)
    FacilityKind.BENCH -> Color(0xFF8D6E63)
}

/**
 * Map showing the user's position (with the way they face, [heading]), nearby spots and
 * amenities, the way already [walked] on this walk, and the way to the spot being guided to. Pans to [focus] when it changes.
 * [onViewportChanged] reports the area on screen (zoom, north, south, east, west) once the
 * map settles, for attributions that depend on it.
 */
@Composable
fun SpotMap(
    tiles: MapTiles,
    location: Location?,
    heading: Float?,
    spots: List<SpotItem>,
    facilities: List<FacilityItem>,
    walked: List<LatLon>,
    route: RouteToSpot?,
    focus: Facility?,
    onSpotClick: (SpotItem) -> Unit,
    onViewportChanged: (zoom: Int, north: Double, south: Double, east: Double, west: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val spotIcon = remember { ContextCompat.getDrawable(context, R.drawable.spot_dot) }
    val meIcon = remember { ContextCompat.getDrawable(context, R.drawable.me_dot) }
    val facilityIcons = remember { FacilityKind.entries.associateWith { ContextCompat.getDrawable(context, facilityDot(it)) } }
    val reportViewport by rememberUpdatedState(onViewportChanged)
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            addMapListener(DelayedMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    reportViewportOf(this@apply, reportViewport)
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    reportViewportOf(this@apply, reportViewport)
                    return false
                }
            }, VIEWPORT_SETTLE_MS))
        }
    }
    val copyright = remember { CopyrightOverlay(context) }
    val markers = remember { MapMarkers() }
    // Recenter only when the user has moved noticeably, so panning isn't constantly undone.
    val centeredAt = remember { arrayOfNulls<Location>(1) }
    val focusedOn = remember { arrayOfNulls<Facility>(1) }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    AndroidView(factory = { mapView }, modifier = modifier) { map ->
        if (tiles != markers.tiles) {
            map.setTileSource(tiles.tileSource())
            markers.tiles = tiles
            // The new tiles may need an attribution for the area already on screen.
            map.post { reportViewportOf(map, reportViewport) }
        }
        // Distances change with every location update; the markers only when the places do,
        // so a bubble the user opened stays open while they walk.
        // Sets: the lists are sorted by distance, so walking reorders them without changing them.
        val facilityIds = facilities.map { it.facility.id }.toSet()
        if (facilityIds != markers.facilityIds) {
            markers.facilities.forEach { it.closeInfoWindow() }
            markers.facilities = facilities.map { item ->
                val f = item.facility
                Marker(map).apply {
                    position = GeoPoint(f.lat, f.lon)
                    icon = facilityIcons[f.kind]
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = f.name?.let { "${f.kind.label}（$it）" } ?: f.kind.label
                }
            }
            markers.facilityIds = facilityIds
        }
        markers.spotsById = spots.associateBy { it.poi.id }
        markers.onSpotClick = onSpotClick
        val spotIds = spots.map { it.poi.id }.toSet()
        if (spotIds != markers.spotIds) {
            markers.spots = spots.map { item ->
                val id = item.poi.id
                Marker(map).apply {
                    position = GeoPoint(item.poi.lat, item.poi.lon)
                    icon = spotIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    // Looked up on tap, so the sheet gets the current distance and visit count.
                    setOnMarkerClickListener { _, _ -> markers.spotsById[id]?.let(markers.onSpotClick); true }
                }
            }
            markers.spotIds = spotIds
        }

        map.overlays.clear()
        // Under the spots, so a bench in a park doesn't cover the park.
        map.overlays += markers.facilities
        // A single point is only where the walk began; the position dot already shows it.
        if (walked.size >= 2) {
            val track = markers.walked ?: Polyline(map).apply {
                outlinePaint.color = WALKED_COLOR
                outlinePaint.strokeWidth = 4 * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                infoWindow = null
            }.also { markers.walked = it }
            // The list is replaced only when the walk has grown, so this skips most updates.
            if (walked !== markers.walkedPoints) {
                track.setPoints(walked.map { GeoPoint(it.lat, it.lon) })
                markers.walkedPoints = walked
            }
            // Under the way ahead, which matters more while walking.
            map.overlays += track
        }
        if (route != null) {
            val line = markers.route ?: Polyline(map).apply {
                outlinePaint.color = ROUTE_COLOR
                outlinePaint.strokeWidth = 5 * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                infoWindow = null
            }.also { markers.route = it }
            // Dashed when it's only the direction, not a way along paths.
            line.outlinePaint.pathEffect = if (route.route.onPaths) null else DashPathEffect(floatArrayOf(12 * density, 8 * density), 0f)
            line.setPoints(route.route.points.map { GeoPoint(it.lat, it.lon) })
            map.overlays += line
        }
        map.overlays += markers.spots
        if (location != null) {
            val me = markers.me ?: Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
            }.also { markers.me = it }
            me.icon = heading?.let { markers.headingIcon(context, it) } ?: meIcon
            me.position = GeoPoint(location.latitude, location.longitude)
            map.overlays += me
            val last = centeredAt[0]
            if (last == null || last.distanceTo(location) > 150f) {
                map.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                centeredAt[0] = location
            }
        }
        if (focus != null && focus != focusedOn[0]) {
            map.controller.animateTo(GeoPoint(focus.lat, focus.lon))
            focusedOn[0] = focus
        }
        // Google's attribution changes with the area and comes with its logo; the screen draws both.
        if (tiles !is MapTiles.Google) map.overlays += copyright
        map.invalidate()
    }
}

/** Markers kept across map updates; rebuilt only when the set of places changes. */
private class MapMarkers {
    var facilityIds: Set<String>? = null
    var facilities: List<Marker> = emptyList()
    var spotIds: Set<String>? = null
    var spots: List<Marker> = emptyList()
    var spotsById: Map<String, SpotItem> = emptyMap()
    var onSpotClick: (SpotItem) -> Unit = {}
    var me: Marker? = null
    var route: Polyline? = null
    var walked: Polyline? = null
    var walkedPoints: List<LatLon>? = null
    var tiles: MapTiles? = null
    private val headingIcons = HashMap<Int, Drawable>()

    /** The position dot with a beam toward [degrees]; one per [HEADING_STEP] degrees, made on first use. */
    fun headingIcon(context: Context, degrees: Float): Drawable {
        val step = ((degrees / HEADING_STEP).roundToInt() * HEADING_STEP) % 360
        return headingIcons.getOrPut(step) { drawHeadingIcon(context, step.toFloat()) }
    }
}

private const val HEADING_STEP = 5
/** Wait until panning or zooming pauses before asking for the attribution of the new area. */
private const val VIEWPORT_SETTLE_MS = 600L

private fun reportViewportOf(
    map: MapView, report: (zoom: Int, north: Double, south: Double, east: Double, west: Double) -> Unit,
) {
    if (map.width == 0 || map.height == 0) return
    val box = map.boundingBox
    report(map.zoomLevelDouble.toInt(), box.latNorth, box.latSouth, box.lonEast, box.lonWest)
}
private const val ROUTE_COLOR = 0xCC1E88E5.toInt()
/** The way walked: apart from the blue of the way ahead and the position dot. */
private const val WALKED_COLOR = 0xB3D81B60.toInt()

/** Same dot as `me_dot`, with a fading beam behind it. Canvas rotation is clockwise, like compass degrees. */
private fun drawHeadingIcon(context: Context, degrees: Float): Drawable {
    val d = context.resources.displayMetrics.density
    val size = (56 * d).roundToInt()
    val c = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.save()
    canvas.rotate(degrees, c, c)
    val beam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(c, c, c, 0x991E88E5.toInt(), 0x001E88E5, Shader.TileMode.CLAMP)
    }
    canvas.drawArc(RectF(0f, 0f, size.toFloat(), size.toFloat()), -90f - 30f, 60f, true, beam)
    canvas.restore()
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E88E5.toInt() }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3 * d
    }
    val r = 10 * d - stroke.strokeWidth / 2
    canvas.drawCircle(c, c, r, fill)
    canvas.drawCircle(c, c, r, stroke)
    return BitmapDrawable(context.resources, bitmap)
}
