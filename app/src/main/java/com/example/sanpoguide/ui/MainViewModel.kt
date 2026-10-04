package com.example.sanpoguide.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sanpoguide.SanpoApp
import com.example.sanpoguide.companion.LiveWalk
import com.example.sanpoguide.companion.Utterance
import com.example.sanpoguide.data.Facility
import com.example.sanpoguide.data.GoogleMapsException
import com.example.sanpoguide.data.FacilityKind
import com.example.sanpoguide.data.Poi
import com.example.sanpoguide.data.RouteGeometry
import com.example.sanpoguide.data.SpotPhoto
import com.example.sanpoguide.data.WalkRoute
import com.example.sanpoguide.history.LatLon
import com.example.sanpoguide.mood.Mood
import com.example.sanpoguide.settings.GuideSettings
import com.example.sanpoguide.settings.MapStyle
import com.example.sanpoguide.station.Station
import com.example.sanpoguide.station.remote.ChannelNotice
import com.example.sanpoguide.walk.WalkService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** A nearby spot; [visitCount] is how many walks have stopped there. */
data class SpotItem(val poi: Poi, val distanceM: Int?, val visitCount: Int)

/** A nearby toilet, drinking fountain or bench. */
data class FacilityItem(val facility: Facility, val distanceM: Int?)

sealed interface GuideState {
    data object Loading : GuideState
    data class Ready(val text: String) : GuideState
    data class Failed(val message: String) : GuideState
}

sealed interface PhotoState {
    /** No photo for this spot, or photos are turned off. */
    data object None : PhotoState
    data object Loading : PhotoState
    /** There is one, but the user only downloads photos on Wi-Fi. */
    data object WaitingForWifi : PhotoState
    data class Ready(val photo: SpotPhoto) : PhotoState
}

/**
 * The way to [spot] drawn on the map; [askedFrom] is where the route was last looked up.
 * While [loading], [route] is a straight placeholder.
 */
data class RouteToSpot(val spot: Poi, val route: WalkRoute, val askedFrom: LatLon, val loading: Boolean = false)

/** What the map shows; [notice] says why it fell back to the GSI map, if it did. */
data class MapState(val tiles: MapTiles, val notice: String? = null)

data class SelectedSpot(val item: SpotItem, val guide: GuideState, val photo: PhotoState = PhotoState.None)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SanpoApp

    val location: StateFlow<Location?> = app.spots.location
    val walking: StateFlow<Boolean> = WalkService.walking
    val settings: StateFlow<GuideSettings> = app.settings.settings

    val mood: StateFlow<Mood> = app.mood.mood

    val station: StateFlow<Station> = app.stations.current
    val stations: StateFlow<List<Station>> = app.stations.list

    /** Things to tell the user about third-party channels (withdrawn, publisher's key moved, list expired). */
    val channelNotices: StateFlow<List<ChannelNotice>> = app.channels.notices

    /** Saved at once: mid-walk, the walk service picks the switch up and the new channel says hello. */
    fun chooseStation(id: String) = app.settings.save(app.settings.settings.value.copy(stationId = id))

    val liveWalk: StateFlow<LiveWalk?> = app.feed.live
    val companionLines: StateFlow<List<Utterance>> = app.feed.lines

    val spots: StateFlow<List<SpotItem>> = combine(
        app.spots.spots, app.spots.location, app.history.walks,
    ) { spots, loc, walks ->
        spots.map { poi ->
            SpotItem(
                poi,
                loc?.let { l -> poi.distanceFrom(l).toInt() },
                visitCount = walks.count { w -> w.visits.any { it.matches(poi) } },
            )
        }
            .sortedBy { it.distanceM ?: Int.MAX_VALUE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val facilities: StateFlow<List<FacilityItem>> = combine(app.spots.facilities, app.spots.location) { list, loc ->
        list.map { FacilityItem(it, loc?.let { l -> it.distanceFrom(l).toInt() }) }
            .sortedBy { it.distanceM ?: Int.MAX_VALUE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The closest of each kind, in [FacilityKind] order. */
    val nearestFacilities: StateFlow<List<FacilityItem>> = facilities.map { list ->
        FacilityKind.entries.mapNotNull { kind -> list.firstOrNull { it.facility.kind == kind } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A facility the user picked from the nearest list; the map pans to it. */
    private val _focus = MutableStateFlow<Facility?>(null)
    val focus: StateFlow<Facility?> = _focus.asStateFlow()

    fun focus(facility: Facility) {
        _focus.value = facility
    }

    /** Bumped by [refresh], so a Google map that failed to connect is tried again. */
    private val mapRetry = MutableStateFlow(0)

    // Declared before [map]: its eager collection runs resolveMap() during construction.
    private val _googleCopyright = MutableStateFlow<String?>(null)
    /** Google's attribution for the area on screen; must be shown while Google tiles are. */
    val googleCopyright: StateFlow<String?> = _googleCopyright.asStateFlow()
    private var copyrightJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val map: StateFlow<MapState> = combine(settings, mapRetry) { s, _ -> s.mapStyle to s.googleMapsApiKey.trim() }
        .mapLatest { (style, key) -> resolveMap(style, key) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapState(MapTiles.Gsi(MapStyle.GSI_STANDARD)))

    private suspend fun resolveMap(style: MapStyle, apiKey: String): MapState {
        _googleCopyright.value = null
        val type = style.google ?: return MapState(MapTiles.Gsi(style))
        val fallback = MapTiles.Gsi(MapStyle.GSI_STANDARD)
        if (apiKey.isEmpty()) return MapState(fallback, "Google マップの APIキーが未設定のため、地理院の地図で表示しています")
        return try {
            MapState(MapTiles.Google(style, type, apiKey, app.googleTiles.session(apiKey, type)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleMapsException) {
            MapState(fallback, "${e.message}。地理院の地図で表示しています")
        } catch (e: Exception) {
            Log.w(TAG, "Map Tiles API session failed", e)
            MapState(fallback, "Google マップに接続できなかったため、地理院の地図で表示しています（再検索で再接続します）")
        }
    }

    /** The map settled on a new area; fetches the attribution for it when Google tiles are shown. */
    fun onMapViewport(zoom: Int, north: Double, south: Double, east: Double, west: Double) {
        val tiles = map.value.tiles as? MapTiles.Google ?: return
        copyrightJob?.cancel()
        copyrightJob = viewModelScope.launch {
            try {
                val text = app.googleTiles.copyright(tiles.apiKey, tiles.session, zoom, north, south, east, west)
                if (map.value.tiles == tiles) _googleCopyright.value = text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Map Tiles API viewport info failed", e)
            }
        }
    }

    /** The spot to show the way to: the one the walk introduced last, or the one the user tapped. */
    private val routeTarget = MutableStateFlow<Poi?>(null)
    private val _route = MutableStateFlow<RouteToSpot?>(null)
    val route: StateFlow<RouteToSpot?> = _route.asStateFlow()

    init {
        viewModelScope.launch {
            app.feed.guiding.filterNotNull().distinctUntilChanged().collect { routeTarget.value = it }
        }
        // Not collectLatest: a location update every few seconds would cancel each lookup in flight.
        viewModelScope.launch {
            combine(routeTarget, app.spots.location) { target, loc -> target to loc }
                .collect { (target, loc) -> updateRoute(target, loc) }
        }
    }

    fun clearRoute() {
        routeTarget.value = null
    }

    private suspend fun updateRoute(target: Poi?, loc: Location?) {
        if (target == null) {
            _route.value = null
            return
        }
        if (loc == null) return
        val here = LatLon(loc.latitude, loc.longitude)
        val to = LatLon(target.lat, target.lon)
        val current = _route.value?.takeIf { it.spot.id == target.id }
        if (current != null) {
            // Look the way up again only when the user has left it, so the routing server
            // (a public one) isn't asked on every step.
            val stillValid = if (current.route.onPaths) {
                RouteGeometry.distanceToLineM(here, current.route.points) < OFF_ROUTE_M
            } else {
                RouteGeometry.distanceM(current.askedFrom, here) < RETRY_AFTER_M
            }
            if (stillValid) {
                // A straight line follows the user; a found route stays as found.
                if (!current.route.onPaths) _route.value = current.copy(route = WalkRoute.straight(here, to))
                return
            }
        } else {
            // Something to look at while the route loads, and what stays if it can't be found.
            _route.value = RouteToSpot(target, WalkRoute.straight(here, to), here, loading = true)
        }
        val found = try {
            app.routes.walk(here, to)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Route lookup failed; drawing a straight line", e)
            WalkRoute.straight(here, to)
        }
        // The user may have picked another spot meanwhile.
        if (routeTarget.value?.id == target.id) _route.value = RouteToSpot(target, found, here)
    }

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selected = MutableStateFlow<SelectedSpot?>(null)
    val selected: StateFlow<SelectedSpot?> = _selected.asStateFlow()

    /** The device's location setting is off, so no position can be had even with the permission. */
    private val _locationOff = MutableStateFlow(false)
    val locationOff: StateFlow<Boolean> = _locationOff.asStateFlow()

    private fun isLocationEnabled(): Boolean =
        app.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) ?: false

    /** Searches again if the user has just turned the location setting on (e.g. back from Settings). */
    fun recheckLocationSetting() {
        if (_locationOff.value && isLocationEnabled()) refresh()
    }

    @SuppressLint("MissingPermission") // Called only after the permission is granted.
    fun refresh() {
        if (map.value.notice != null) mapRetry.update { it + 1 }
        if (_loading.value) return
        _locationOff.value = !isLocationEnabled()
        if (_locationOff.value) {
            _error.value = null
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val fused = LocationServices.getFusedLocationProviderClient(app)
                val loc = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                    ?: fused.lastLocation.await()
                if (loc == null) {
                    _error.value = "現在地を取得できませんでした"
                } else {
                    app.spots.onLocation(loc, forceRefresh = true)
                    if (app.spots.spots.value.isEmpty()) _error.value = "周辺にスポットが見つかりませんでした"
                }
            } catch (e: Exception) {
                _error.value = "スポットの検索に失敗しました: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun select(item: SpotItem) {
        routeTarget.value = item.poi
        val photo = initialPhotoState(item.poi)
        _selected.value = SelectedSpot(item, GuideState.Loading, photo)
        viewModelScope.launch {
            val state = try {
                GuideState.Ready(app.guides.guideFor(item.poi, item.distanceM))
            } catch (e: Exception) {
                GuideState.Failed("解説の生成に失敗しました: ${e.message}")
            }
            updateSelected(item) { it.copy(guide = state) }
        }
        if (photo == PhotoState.Loading) {
            viewModelScope.launch {
                val found = app.photos.photoFor(item.poi)
                updateSelected(item) { it.copy(photo = found?.let(PhotoState::Ready) ?: PhotoState.None) }
            }
        }
    }

    private fun initialPhotoState(poi: Poi): PhotoState {
        val s = settings.value
        return when {
            !s.spotPhotos || !app.photos.hasPhotoHint(poi) -> PhotoState.None
            app.photos.isMetered() && !s.photosOnMobileData -> PhotoState.WaitingForWifi
            else -> PhotoState.Loading
        }
    }

    /** Ignores results for a spot the user has already moved on from. */
    private fun updateSelected(item: SpotItem, change: (SelectedSpot) -> SelectedSpot) =
        _selected.update { current -> if (current?.item?.poi?.id == item.poi.id) change(current) else current }

    fun dismiss() {
        _selected.value = null
        app.speaker.stop()
    }

    fun speak(poi: Poi, text: String) {
        app.speaker.stop()
        app.speaker.speak("${poi.name}。$text")
    }

    fun stopSpeaking() = app.speaker.stop()

    private companion object {
        const val TAG = "MainViewModel"
        /** Farther than this from the drawn route, the user has taken another way. */
        const val OFF_ROUTE_M = 30.0
        /** After a failed lookup, try again once the user has moved this far. */
        const val RETRY_AFTER_M = 100.0
    }
}
