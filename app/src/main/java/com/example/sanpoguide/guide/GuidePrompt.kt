package com.example.sanpoguide.guide

import com.example.sanpoguide.data.Poi

/** Values for the spot-guide prompt files (`guide/user`, `fallback/guide`). The wording lives in the files. */
object GuidePrompt {
    /** OSM tags worth showing the model; the rest (addresses, operators, ids...) is noise. */
    private val USEFUL_TAGS = setOf(
        "historic", "tourism", "amenity", "religion", "denomination", "leisure", "natural",
        "heritage", "start_date", "architect", "description", "inscription", "species",
        "wikipedia", "wikidata", "opening_hours", "website", "ele",
    )

    fun spotVars(poi: Poi, distanceM: Int?): Map<String, Any?> = mapOf(
        "name" to poi.name,
        "category" to poi.category,
        "distance_m" to distanceM,
        "tags" to poi.tags.filterKeys { it in USEFUL_TAGS || it.startsWith("name") }
            .map { (k, v) -> "$k=$v" },
    )

    fun fallbackVars(poi: Poi): Map<String, Any?> = mapOf(
        "name" to poi.name,
        "category" to poi.category,
        "description" to poi.tags["description"],
        "start_date" to poi.tags["start_date"],
    )
}
