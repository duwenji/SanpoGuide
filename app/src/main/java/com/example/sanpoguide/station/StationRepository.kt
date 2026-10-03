package com.example.sanpoguide.station

import com.example.sanpoguide.settings.GuideSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The available channels, and the one in use with the user's changes applied. */
class StationRepository(val all: List<Station>, settings: StateFlow<GuideSettings>, scope: CoroutineScope) {
    val standard: Station = all.first { it.id == BuiltInStations.STANDARD }

    val current: StateFlow<Station> = settings
        .map(::select)
        .stateIn(scope, SharingStarted.Eagerly, select(settings.value))

    /** The chosen channel, or the standard one if it's gone (e.g. removed in an update). */
    fun select(settings: GuideSettings): Station {
        val station = all.firstOrNull { it.id == settings.stationId } ?: standard
        return station.withOverrides(settings.stationOverrides[station.id] ?: StationOverrides())
    }

    fun byId(id: String): Station = all.firstOrNull { it.id == id } ?: standard
}
