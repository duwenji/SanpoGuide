package com.example.sanpoguide.station

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The available channels and the one in use. For now only the standard channel exists, so the
 * app behaves as before; choosing and switching channels come next (docs/channels.md「進め方」).
 */
class StationRepository(val all: List<Station>) {
    private val _current = MutableStateFlow(all.first { it.manifest.id == BuiltInStations.STANDARD })
    val current: StateFlow<Station> = _current.asStateFlow()
}
