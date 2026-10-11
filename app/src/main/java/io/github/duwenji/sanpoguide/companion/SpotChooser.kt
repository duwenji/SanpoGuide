package io.github.duwenji.sanpoguide.companion

import io.github.duwenji.sanpoguide.data.Poi

/** A spot within reach that hasn't been talked about on this walk. */
data class SpotCandidate(
    val poi: Poi,
    val distanceM: Float,
    /** Visited on an earlier walk: the companion would say "welcome back" rather than guide. */
    val visitedBefore: Boolean,
)

/** Picks the one spot to talk about next, the way the channel in use wants it. */
object SpotChooser {
    /** Lifts a preferred kind to the level of a well-documented spot of another kind. */
    const val PREFER_BONUS = 2

    /**
     * @param prefer categories (`Poi.category`) to bring up first
     * @param skip categories not to bring up on walks (they stay on the map and the list)
     * @param newSpots whether to guide at spots never visited before
     * @param revisits whether to remark on spots visited on earlier walks
     */
    fun pick(
        candidates: List<SpotCandidate>,
        prefer: Set<String>,
        skip: Set<String>,
        newSpots: Boolean,
        revisits: Boolean,
    ): SpotCandidate? = candidates
        .filter { it.poi.category !in skip }
        .filter { if (it.visitedBefore) revisits else newSpots }
        // The candidates not picked stay candidates: they get their turn once the gap passes, if still in reach.
        .sortedWith(compareByDescending<SpotCandidate> { score(it.poi, prefer) }.thenBy { it.distanceM })
        .firstOrNull()

    private fun score(poi: Poi, prefer: Set<String>): Int = poi.notability + if (poi.category in prefer) PREFER_BONUS else 0
}
