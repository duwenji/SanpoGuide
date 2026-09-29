package com.example.sanpoguide.companion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A line the companion said, as shown in the app. */
data class Utterance(val at: Long, val text: String, val spotName: String? = null)

/** Live walk figures for the UI; null while not walking. */
data class LiveWalk(val startedAt: Long, val distanceM: Double, val spotCount: Int)

/** What the walk service publishes for the UI: live stats and the companion's recent lines. */
class CompanionFeed {
    private val _live = MutableStateFlow<LiveWalk?>(null)
    val live: StateFlow<LiveWalk?> = _live.asStateFlow()

    private val _lines = MutableStateFlow<List<Utterance>>(emptyList())
    /** Newest first. */
    val lines: StateFlow<List<Utterance>> = _lines.asStateFlow()

    private val _weather = MutableStateFlow<Weather?>(null)
    /** The latest weather while walking (fetched every 15 minutes); null outside walk mode. */
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private val _talkedAbout = MutableStateFlow<Set<String>>(emptySet())
    /** Spot ids talked about during the current walk. */
    val talkedAbout: StateFlow<Set<String>> = _talkedAbout.asStateFlow()

    fun startWalk(session: WalkSession) {
        _lines.value = emptyList()
        _talkedAbout.value = emptySet()
        update(session)
    }

    fun update(session: WalkSession) {
        _live.value = LiveWalk(session.startedAt, session.distanceM, session.visits.size)
        _talkedAbout.value = session.talkedAbout.toSet()
    }

    fun endWalk() {
        _live.value = null
        _weather.value = null
    }

    fun updateWeather(weather: Weather?) {
        _weather.value = weather
    }

    fun add(line: Utterance) = _lines.update { (listOf(line) + it).take(MAX_LINES) }

    private companion object {
        const val MAX_LINES = 30
    }
}
