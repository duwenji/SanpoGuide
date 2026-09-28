package com.example.sanpoguide.ui

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import org.osmdroid.views.overlay.infowindow.InfoWindow

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
    // Recenter only when the user has moved noticeably, so panning isn't constantly undone.
    val centeredAt = remember { arrayOfNulls<Location>(1) }
    val focusedOn = remember { arrayOfNulls<Facility>(1) }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    AndroidView(factory = { mapView }, modifier = modifier) { map ->
        // Markers are rebuilt below; an open bubble would be left pointing at a removed one.
        InfoWindow.closeAllInfoWindowsOn(map)
        map.overlays.clear()
        // Under the spots, so a bench in a park doesn't cover the park.
        facilities.forEach { item ->
            val f = item.facility
            map.overlays += Marker(map).apply {
                position = GeoPoint(f.lat, f.lon)
                icon = facilityIcons[f.kind]
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = f.name?.let { "${f.kind.label}（$it）" } ?: f.kind.label
            }
        }
        spots.forEach { item ->
            map.overlays += Marker(map).apply {
                position = GeoPoint(item.poi.lat, item.poi.lon)
                icon = spotIcon
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> onSpotClick(item); true }
            }
        }
        if (location != null) {
            map.overlays += Marker(map).apply {
                position = GeoPoint(location.latitude, location.longitude)
                icon = meIcon
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
            }
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
