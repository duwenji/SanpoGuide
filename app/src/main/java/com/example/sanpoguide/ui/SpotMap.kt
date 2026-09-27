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

/** Map showing the user's position and nearby spots. */
@Composable
fun SpotMap(
    location: Location?,
    spots: List<SpotItem>,
    onSpotClick: (SpotItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spotIcon = remember { ContextCompat.getDrawable(context, R.drawable.spot_dot) }
    val meIcon = remember { ContextCompat.getDrawable(context, R.drawable.me_dot) }
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

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    AndroidView(factory = { mapView }, modifier = modifier) { map ->
        map.overlays.clear()
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
        map.overlays += copyright
        map.invalidate()
    }
}
