package com.example.sanpoguide.ui

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.sanpoguide.R
import com.example.sanpoguide.data.Facility
import com.example.sanpoguide.data.FacilityKind
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

/** GSI (Geospatial Information Authority of Japan) standard map tiles. No API key needed. */
private val GsiStandard = XYTileSource(
    "GSI_std", 5, 18, 256, ".png",
    arrayOf("https://cyberjapandata.gsi.go.jp/xyz/std/"),
    "出典：国土地理院",
)

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

/** Map showing the user's position, nearby spots and amenities. Pans to [focus] when it changes. */
@Composable
fun SpotMap(
    location: Location?,
    spots: List<SpotItem>,
    facilities: List<FacilityItem>,
    focus: Facility?,
    onSpotClick: (SpotItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spotIcon = remember { ContextCompat.getDrawable(context, R.drawable.spot_dot) }
    val meIcon = remember { ContextCompat.getDrawable(context, R.drawable.me_dot) }
    val facilityIcons = remember { FacilityKind.entries.associateWith { ContextCompat.getDrawable(context, facilityDot(it)) } }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(GsiStandard)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
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
        map.overlays += markers.spots
        if (location != null) {
            val me = markers.me ?: Marker(map).apply {
                icon = meIcon
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
            }.also { markers.me = it }
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
        map.overlays += copyright
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
}
