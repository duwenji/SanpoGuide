package com.example.sanpoguide.settings

/** Google's map types in the Map Tiles API (`mapType` of a session). */
enum class GoogleMapType(val apiName: String) {
    ROADMAP("roadmap"),
    SATELLITE("satellite"),
}

/**
 * The map under the spots. The GSI maps need no key; the Google ones use the user's own
 * Map Tiles API key (Google Cloud, billed to them).
 */
enum class MapStyle(val label: String, val description: String, val google: GoogleMapType? = null) {
    GSI_STANDARD("地理院 標準", "国土地理院の標準地図"),
    GSI_PALE("地理院 淡色", "色を抑えた地図。スポットやルートの線が目立ちます"),
    GSI_PHOTO("地理院 写真", "国土地理院の航空写真"),
    GSI_RELIEF("地理院 標高", "高さを色で表した地図。坂や高低差がわかります（拡大は少し粗め）"),
    GOOGLE_ROADMAP("Google 地図", "Google マップの地図。Map Tiles API のキーが必要です", GoogleMapType.ROADMAP),
    GOOGLE_SATELLITE("Google 航空写真", "Google マップの航空写真。Map Tiles API のキーが必要です", GoogleMapType.SATELLITE),
    ;

    val needsGoogleKey: Boolean get() = google != null
}
