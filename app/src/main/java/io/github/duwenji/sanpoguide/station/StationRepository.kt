package io.github.duwenji.sanpoguide.station

import io.github.duwenji.sanpoguide.history.WalkRecord
import io.github.duwenji.sanpoguide.settings.GuideSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The available channels (the built-in ones, then third parties' usable now), and the one in use
 * with the user's changes applied.
 */
class StationRepository(
    builtIn: List<Station>,
    settings: StateFlow<GuideSettings>,
    scope: CoroutineScope,
    thirdParty: StateFlow<List<Station>> = MutableStateFlow(emptyList()),
) {
    val standard: Station = builtIn.first { it.id == BuiltInStations.STANDARD }

    val list: StateFlow<List<Station>> = combine(MutableStateFlow(builtIn), thirdParty) { b, t -> b + t }
        .stateIn(scope, SharingStarted.Eagerly, builtIn + thirdParty.value)

    val all: List<Station> get() = list.value

    val current: StateFlow<Station> = combine(settings, list) { s, l -> select(s, l) }
        .stateIn(scope, SharingStarted.Eagerly, select(settings.value, list.value))

    /** The chosen channel, or the standard one if it's gone (removed in an update, withdrawn, or its list expired). */
    fun select(settings: GuideSettings, from: List<Station> = all): Station {
        val station = from.firstOrNull { it.id == settings.stationId } ?: standard
        return station.withOverrides(settings.stationOverrides[station.id] ?: StationOverrides())
    }

    fun byId(id: String): Station = all.firstOrNull { it.id == id } ?: standard

    /**
     * The channels of a walk in the history, in order (`標準 → 歴史探訪`). A walk from before
     * channels was on the standard one; a channel the app no longer has shows as such.
     */
    fun namesOf(walk: WalkRecord): String {
        val keys = walk.stations.map { it.station }.ifEmpty { listOf(standard.key) }
        return keys.map { key -> all.firstOrNull { sameChannel(it.key, key) }?.name ?: "（今はないチャンネル）" }
            .fold(emptyList<String>()) { acc, name -> if (acc.lastOrNull() == name) acc else acc + name }
            .joinToString(" → ")
    }

    // A newer version of the same channel still counts as that channel.
    private fun sameChannel(a: String, b: String) = a.substringBefore('@') == b.substringBefore('@')
}
