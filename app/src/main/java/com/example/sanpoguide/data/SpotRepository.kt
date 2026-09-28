package com.example.sanpoguide.data

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Current location, nearby spots and amenities, shared by the UI and the walk service. */
class SpotRepository(private val overpass: OverpassClient) {
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _spots = MutableStateFlow<List<Poi>>(emptyList())
    val spots: StateFlow<List<Poi>> = _spots.asStateFlow()

    private val _facilities = MutableStateFlow<List<Facility>>(emptyList())
    val facilities: StateFlow<List<Facility>> = _facilities.asStateFlow()

    private val fetchLock = Mutex()
    private var lastFetchAt: Location? = null

    /** Updates the location, re-querying spots only after moving [REFETCH_DISTANCE_M]. */
    suspend fun onLocation(location: Location, forceRefresh: Boolean = false) {
        _location.value = location
        fetchLock.withLock {
            val last = lastFetchAt
            if (forceRefresh || last == null || last.distanceTo(location) > REFETCH_DISTANCE_M) {
                val found = overpass.search(location.latitude, location.longitude)
                _spots.value = found.spots
                _facilities.value = found.facilities
                lastFetchAt = location
            }
        }
    }

    companion object {
        private const val REFETCH_DISTANCE_M = 300f
    }
}
