package io.github.duwenji.sanpoguide.ui

import io.github.duwenji.sanpoguide.data.GoogleMapTiles
import io.github.duwenji.sanpoguide.data.GoogleTileSession
import io.github.duwenji.sanpoguide.settings.GoogleMapType
import io.github.duwenji.sanpoguide.settings.MapStyle
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/** The tiles the map shows, resolved from the setting (a Google style needs a live session). */
sealed interface MapTiles {
    val style: MapStyle

    data class Gsi(override val style: MapStyle) : MapTiles

    data class Google(
        override val style: MapStyle,
        val type: GoogleMapType,
        val apiKey: String,
        val session: GoogleTileSession,
    ) : MapTiles
}

/**
 * GSI (Geospatial Information Authority of Japan) tiles need no key; the attribution is
 * drawn by the map's copyright overlay. https://maps.gsi.go.jp/development/ichiran.html
 */
private fun gsi(name: String, layer: String, minZoom: Int, maxZoom: Int, ext: String, credit: String) =
    XYTileSource(name, minZoom, maxZoom, 256, ext, arrayOf("https://cyberjapandata.gsi.go.jp/xyz/$layer/"), credit)

private val GsiStandard = gsi("GSI_std", "std", 5, 18, ".png", "出典：国土地理院")
private val GsiPale = gsi("GSI_pale", "pale", 5, 18, ".png", "出典：国土地理院")
private val GsiPhoto = gsi(
    "GSI_photo", "seamlessphoto", 2, 18, ".jpg",
    "出典：国土地理院（全国最新写真）、Landsat8画像（GSI,TSIC,GEO Grid/AIST）、海底地形（GEBCO）",
)
private val GsiRelief = gsi("GSI_relief", "relief", 5, 15, ".png", "出典：国土地理院（色別標高図）、海上保安庁海洋情報部")

/**
 * Google Map Tiles API 2D tiles. The attribution isn't fixed: it depends on the area, so it
 * comes from the viewport endpoint and is drawn by the screen with the Google Maps logo.
 */
private class GoogleTileSource(private val tiles: MapTiles.Google) : OnlineTileSourceBase(
    // The name is the cache key; the session isn't part of it, so a new session reuses the cache.
    "Google_${tiles.type.apiName}", 0, if (tiles.type == GoogleMapType.SATELLITE) 20 else 21, 256, "",
    arrayOf(GoogleMapTiles.BASE), null,
) {
    override fun getTileURLString(pMapTileIndex: Long): String = GoogleMapTiles.tileUrl(
        MapTileIndex.getZoom(pMapTileIndex), MapTileIndex.getX(pMapTileIndex), MapTileIndex.getY(pMapTileIndex),
        tiles.session, tiles.apiKey,
    )
}

fun MapTiles.tileSource(): ITileSource = when (this) {
    is MapTiles.Google -> GoogleTileSource(this)
    is MapTiles.Gsi -> when (style) {
        MapStyle.GSI_PALE -> GsiPale
        MapStyle.GSI_PHOTO -> GsiPhoto
        MapStyle.GSI_RELIEF -> GsiRelief
        else -> GsiStandard
    }
}
