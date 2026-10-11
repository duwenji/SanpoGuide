package io.github.duwenji.sanpoguide.data

import android.location.Location
import io.github.duwenji.sanpoguide.station.format.SpotKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Current location, nearby spots and amenities, shared by the UI and the walk service.
 * [shops] gives the eating and shopping kinds the channel in use adds to the search.
 */
class SpotRepository(private val overpass: OverpassClient, private val shops: () -> Set<SpotKind> = { emptySet() }) {
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _spots = MutableStateFlow<List<Poi>>(emptyList())
    val spots: StateFlow<List<Poi>> = _spots.asStateFlow()

    private val _facilities = MutableStateFlow<List<Facility>>(emptyList())
    val facilities: StateFlow<List<Facility>> = _facilities.asStateFlow()

    private val fetchLock = Mutex()
    private var lastFetchAt: Location? = null
    private var lastShops: Set<SpotKind> = emptySet()

    /**
     * Updates the location, re-querying spots only after moving [REFETCH_DISTANCE_M] or when
     * the channel now wants other shops.
     */
    suspend fun onLocation(location: Location, forceRefresh: Boolean = false) {
        _location.value = location
        fetchLock.withLock {
            val last = lastFetchAt
            val shops = shops()
            if (forceRefresh || last == null || last.distanceTo(location) > REFETCH_DISTANCE_M || shops != lastShops) {
                val found = overpass.search(location.latitude, location.longitude, shops = shops)
                _spots.value = found.spots
                _facilities.value = found.facilities
                lastFetchAt = location
                lastShops = shops
            }
        }
    }

    /** Searches again where the user is, if the channel's shops changed (a switch of channel). */
    suspend fun onShopsChanged() {
        _location.value?.let { onLocation(it) }
    }

    companion object {
        private const val REFETCH_DISTANCE_M = 300f
    }
}
