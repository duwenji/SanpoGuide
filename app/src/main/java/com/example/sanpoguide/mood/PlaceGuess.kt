package com.example.sanpoguide.mood

/** Guesses the kind of place from the spots around the user. Pure logic, no Android calls. */
object PlaceGuess {
    /** Spots farther than this don't set the feel of where the user is. */
    const val NEAR_M = 150f

    /**
     * @param nearby spot categories with their distance in meters (0 when inside the spot's area)
     * @return the place of an area the user is inside (a shrine's grounds, a park); otherwise the
     * most common place among spots within [NEAR_M], the closest breaking a tie; [Place.TOWN] if none
     */
    fun of(nearby: List<Pair<String, Float>>): Place {
        nearby.filter { it.second <= 0f }.mapNotNull { Place.ofCategory(it.first) }.minOrNull()?.let { return it }
        val near = nearby.filter { it.second <= NEAR_M }
            .mapNotNull { (category, d) -> Place.ofCategory(category)?.let { it to d } }
        return near.groupBy({ it.first }, { it.second })
            .maxWithOrNull(compareBy<Map.Entry<Place, List<Float>>> { it.value.size }.thenByDescending { it.value.min() })
            ?.key ?: Place.TOWN
    }
}

/**
 * Keeps the place from flickering at the edge of a park or while GPS wanders: a new place is
 * taken only after it has been seen for [holdMs]. The first place is taken at once.
 */
class PlaceSmoother(private val holdMs: Long = HOLD_MS) {
    var current: Place? = null
        private set
    private var candidate: Place? = null
    private var candidateSince = 0L

    fun update(observed: Place, now: Long): Place {
        val cur = current
        if (cur == null || observed == cur) {
            current = observed
            candidate = null
            return observed
        }
        if (observed != candidate) {
            candidate = observed
            candidateSince = now
        } else if (now - candidateSince >= holdMs) {
            current = observed
            candidate = null
        }
        return current!!
    }

    companion object {
        const val HOLD_MS = 3 * 60_000L
    }
}
