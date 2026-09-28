package com.example.sanpoguide.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sanpoguide.SanpoApp
import com.example.sanpoguide.companion.LiveWalk
import com.example.sanpoguide.companion.Utterance
import com.example.sanpoguide.data.Facility
import com.example.sanpoguide.data.FacilityKind
import com.example.sanpoguide.data.Poi
import com.example.sanpoguide.settings.GuideSettings
import com.example.sanpoguide.walk.WalkService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

data class SelectedSpot(val item: SpotItem, val guide: GuideState)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SanpoApp

    val location: StateFlow<Location?> = app.spots.location
    val walking: StateFlow<Boolean> = WalkService.walking
    val settings: StateFlow<GuideSettings> = app.settings.settings

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

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selected = MutableStateFlow<SelectedSpot?>(null)
    val selected: StateFlow<SelectedSpot?> = _selected.asStateFlow()

    @SuppressLint("MissingPermission") // Called only after the permission is granted.
    fun refresh() {
        if (_loading.value) return
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
        _selected.value = SelectedSpot(item, GuideState.Loading)
        viewModelScope.launch {
            val state = try {
                GuideState.Ready(app.guides.guideFor(item.poi, item.distanceM))
            } catch (e: Exception) {
                GuideState.Failed("解説の生成に失敗しました: ${e.message}")
            }
            // Ignore the result if the user has already moved on to another spot.
            if (_selected.value?.item?.poi?.id == item.poi.id) {
                _selected.value = SelectedSpot(item, state)
            }
        }
    }

    fun dismiss() {
        _selected.value = null
        app.speaker.stop()
    }

    fun speak(poi: Poi, text: String) {
        app.speaker.stop()
        app.speaker.speak("${poi.name}。$text")
    }

    fun stopSpeaking() = app.speaker.stop()
}
