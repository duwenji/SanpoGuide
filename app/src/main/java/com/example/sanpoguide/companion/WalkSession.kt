package com.example.sanpoguide.companion

import android.location.Location
import com.example.sanpoguide.data.RouteGeometry
import com.example.sanpoguide.history.LatLon
import com.example.sanpoguide.history.SpotVisit
import com.example.sanpoguide.history.StationSegment
import com.example.sanpoguide.history.WalkRecord

/** Mutable state of the walk in progress. Owned and mutated by the walk service only. */
class WalkSession(val startedAt: Long = System.currentTimeMillis()) {
    val id: Long = startedAt
    var distanceM = 0.0
        private set
    private var lastCounted: Location? = null
    private val route = mutableListOf<LatLon>()
    val visits = mutableListOf<SpotVisit>()

    /** Spots already talked about during this walk; each is mentioned once per walk. */
    val talkedAbout = mutableSetOf<String>()

    /** When the companion last said anything, to keep small talk spaced out. */
    var lastSpokeAt = startedAt
    var lastSpotTalkAt = 0L
    var lastMilestoneAt = startedAt
    var lastMilestoneDistanceM = 0.0

    private var restAnchor: Location? = null
    private var restSince = 0L
    var restRemarked = false
        private set

    /** Whether the companion has mentioned the coming sunset; once per walk. */
    var sunsetWarned = false

    /** When each kind of weather change was last warned about. */
    val weatherWarnedAt = mutableMapOf<WeatherChangeKind, Long>()

    /** Facilities mentioned on this walk (each once), and when each need last came up. */
    val mentionedFacilities = mutableSetOf<String>()
    val facilityMentionAt = mutableMapOf<FacilityNeed, Long>()

    /** Recent lines, so the model can avoid repeating itself. */
    val recentLines = ArrayDeque<String>()

    /** The channels used on this walk, in order (docs/channels.md「記録」). */
    val stations = mutableListOf<StationSegment>()

    /** When the user last switched channels mid-walk; 0 if they haven't. */
    var stationSwitchedAt = 0L
        private set

    /** The new channel's greeting, to be said before anything else but weather warnings. */
    var pendingGreeting: String? = null

    /** The channel the walk starts on; a switch later goes through [switchStation]. */
    fun startStation(key: String) {
        stations += StationSegment(key, startedAt)
    }

    /** Moves to another channel: what was said and where carries over, only the voice changes. */
    fun switchStation(key: String, greeting: String, now: Long = System.currentTimeMillis()) {
        if (stations.lastOrNull()?.station == key) return
        stations += StationSegment(key, now)
        stationSwitchedAt = now
        pendingGreeting = greeting
    }

    /** Right after a switch the greeting has the floor; spot talks and small talk wait a moment. */
    fun justSwitched(now: Long = System.currentTimeMillis()): Boolean =
        stationSwitchedAt > 0 && now - stationSwitchedAt < SWITCH_QUIET_MS

    fun elapsedMs(now: Long = System.currentTimeMillis()) = now - startedAt

    /** Adds movement to the distance, ignoring GPS jitter and poor fixes. */
    fun onLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return
        val last = lastCounted
        if (last == null) {
            lastCounted = location
            route += LatLon(location.latitude, location.longitude)
        } else {
            val step = last.distanceTo(location)
            if (step >= MIN_STEP_M) {
                distanceM += step
                lastCounted = location
                if (route.size < MAX_ROUTE_POINTS) route += LatLon(location.latitude, location.longitude)
            }
        }
        trackRest(location)
    }

    /** How many points of the route walked so far have been recorded; grows as the user walks. */
    val routeSize: Int get() = route.size

    /** The route walked so far, every recorded point (a copy). */
    fun walkedRoute(): List<LatLon> = route.toList()

    /** The route walked so far, cut down to at most [maxPoints] evenly spread points; ends at the latest one. */
    fun routeSketch(maxPoints: Int): List<LatLon> = RouteGeometry.thin(route, maxPoints)

    /** Minutes the user has stayed within [REST_RADIUS_M], or 0 if moving. */
    fun restMinutes(now: Long = System.currentTimeMillis()): Int =
        if (restAnchor == null) 0 else ((now - restSince) / 60_000).toInt()

    fun markRestRemarked() {
        restRemarked = true
    }

    fun remember(line: String) {
        recentLines.addLast(line)
        while (recentLines.size > 4) recentLines.removeFirst()
        lastSpokeAt = System.currentTimeMillis()
    }

    fun toRecord(endedAt: Long = System.currentTimeMillis()) = WalkRecord(
        id = id, startedAt = startedAt, endedAt = endedAt, distanceM = distanceM,
        route = route.toList(), visits = visits.toList(), stations = stations.toList(),
    )

    private fun trackRest(location: Location) {
        val anchor = restAnchor
        if (anchor == null || anchor.distanceTo(location) > REST_RADIUS_M) {
            restAnchor = location
            restSince = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
            restRemarked = false
        }
    }

    private companion object {
        const val MAX_ACCURACY_M = 50f
        const val MIN_STEP_M = 8f
        const val REST_RADIUS_M = 30f
        const val MAX_ROUTE_POINTS = 3000
        const val SWITCH_QUIET_MS = 60_000L
    }
}
