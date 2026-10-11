package io.github.duwenji.sanpoguide.mood

import io.github.duwenji.sanpoguide.companion.Weather
import io.github.duwenji.sanpoguide.data.SpotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/**
 * The current [Mood], shared by the screen, the background sound and the companion.
 * The weather comes from walk mode only, so outside a walk the mood is time and season (and
 * the place, once spots have been searched).
 */
class MoodSource(spots: SpotRepository, weather: StateFlow<Weather?>, scope: CoroutineScope) {
    private val smoother = PlaceSmoother()

    /** Time of day moves on even when nothing else changes. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_MS)
        }
    }

    val mood: StateFlow<Mood> = combine(clock, spots.location, spots.spots, weather) { now, location, list, w ->
        val place = if (location != null && list.isNotEmpty()) {
            smoother.update(PlaceGuess.of(list.map { it.category to it.distanceFrom(location) }), now)
        } else {
            smoother.current
        }
        Mood.at(Calendar.getInstance().apply { timeInMillis = now }, Sky.of(w?.code), place)
    }.stateIn(scope, SharingStarted.Eagerly, Mood.at(Calendar.getInstance()))

    private companion object {
        const val CLOCK_MS = 60_000L
    }
}
