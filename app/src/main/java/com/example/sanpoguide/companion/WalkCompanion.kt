package com.example.sanpoguide.companion

import android.util.Log
import com.example.sanpoguide.data.Poi
import com.example.sanpoguide.guide.GuidePrompt
import com.example.sanpoguide.guide.GuideRepository
import com.example.sanpoguide.history.HistoryStore
import com.example.sanpoguide.history.SpotVisit
import com.example.sanpoguide.history.WalkRecord
import com.example.sanpoguide.mood.Mood
import com.example.sanpoguide.mood.Season
import com.example.sanpoguide.mood.TimeOfDay
import com.example.sanpoguide.prompt.PromptTemplates
import com.example.sanpoguide.prompt.Prompts
import com.example.sanpoguide.station.Station
import com.example.sanpoguide.station.format.TalkEventKind
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Something that happened on the walk that the companion may talk about. */
sealed interface TalkEvent {
    data object Start : TalkEvent
    data class Revisit(val poi: Poi, val pastVisits: List<SpotVisit>) : TalkEvent
    data object Milestone : TalkEvent
    data class Rest(val minutes: Int) : TalkEvent
    data class Sunset(val minutes: Int) : TalkEvent
    data class WeatherTurn(val change: WeatherChange) : TalkEvent
    /** [direction] is relative to the walking direction, or null when the heading is unknown. */
    data class NearFacility(val advice: FacilityAdvice, val direction: String?) : TalkEvent
    data class Finish(val walk: WalkRecord) : TalkEvent
}

/**
 * The walking companion's voice: turns an event plus the current situation (time, season,
 * weather, this walk, past walks) into one short, natural line.
 *
 * This class only gathers the values; all wording is in the prompt files under
 * `assets/prompts/companion/` and `assets/prompts/fallback/companion/`, plus the slots of the
 * channel in use.
 */
class WalkCompanion(
    private val guides: GuideRepository,
    private val history: HistoryStore,
    private val prompts: PromptTemplates,
    /** The channel in use; its slots fill the system prompt and add to the event prompts. */
    private val station: () -> Station,
    /** The current mood for the companion's tone, or null when the user has turned moods off. */
    private val mood: () -> Mood? = { null },
    /** Whether the user allows coordinates to go to the AI (off by default). */
    private val shareLocation: () -> Boolean = { false },
) {
    suspend fun say(event: TalkEvent, session: WalkSession?, weather: Weather?): String {
        val kind = kindOf(event)
        val eventVars = eventVars(event)
        val fallback = prompts.render(Prompts.Fallback.companion(kind), fallbackVars(event, session, eventVars))
        return try {
            val channel = station()
            val extra = stationEventOf(kind)?.let(channel::eventInstructions)
                ?.let { "\n\n" + prompts.render(Prompts.Talk.STATION_EVENT, mapOf("text" to it)) }
                .orEmpty()
            val user = prompts.render(Prompts.Talk.SITUATION, situationVars(event, session, weather)) +
                "\n\n" + prompts.render(Prompts.event(kind), eventVars) + extra
            guides.chat(prompts.render(Prompts.Talk.SYSTEM, channel.companionSystemVars()), user)
                ?.takeIf { it.isNotBlank() }
                ?: fallback
        } catch (e: Exception) {
            Log.w(TAG, "Companion line failed; using fallback", e)
            fallback
        }
    }

    private fun kindOf(event: TalkEvent) = when (event) {
        TalkEvent.Start -> Prompts.Event.START
        is TalkEvent.Revisit -> Prompts.Event.REVISIT
        TalkEvent.Milestone -> Prompts.Event.MILESTONE
        is TalkEvent.Rest -> Prompts.Event.REST
        is TalkEvent.Sunset -> Prompts.Event.SUNSET
        is TalkEvent.WeatherTurn -> Prompts.Event.WEATHER_CHANGE
        is TalkEvent.NearFacility -> Prompts.Event.FACILITY
        is TalkEvent.Finish -> Prompts.Event.FINISH
    }

    /** The events a channel may add instructions to; the safety ones (weather, sunset, amenities) take none. */
    private fun stationEventOf(kind: Prompts.Event): TalkEventKind? = when (kind) {
        Prompts.Event.START -> TalkEventKind.START
        Prompts.Event.REVISIT -> TalkEventKind.REVISIT
        Prompts.Event.MILESTONE -> TalkEventKind.MILESTONE
        Prompts.Event.REST -> TalkEventKind.REST
        Prompts.Event.FINISH -> TalkEventKind.FINISH
        Prompts.Event.SUNSET, Prompts.Event.WEATHER_CHANGE, Prompts.Event.FACILITY -> null
    }

    /** Variables for `companion/situation` (documented at the top of that file). */
    private fun situationVars(event: TalkEvent, session: WalkSession?, weather: Weather?): Map<String, Any?> {
        val now = Calendar.getInstance()
        val nowMs = now.timeInMillis
        val finishedId = (event as? TalkEvent.Finish)?.walk?.id
        val past = history.walks.value.filter { it.id != session?.id && it.id != finishedId }
        val last = past.firstOrNull()
        val weekAgo = nowMs - TimeUnit.DAYS.toMillis(7)
        val todayWalks = past.count { daysBetween(it.startedAt, nowMs) == 0 }

        return mapOf(
            "now" to formatDateTime(now),
            "time_of_day" to TimeOfDay.of(now).label,
            "season" to Season.of(now).label,
            // Spot lines should be about the spot, like the weather below.
            "mood" to mood()?.takeIf { event !is TalkEvent.Revisit }?.let { mapOf("summary" to it.summary) },
            // Spot lines should be about the spot; given the weather, models mention it every time.
            "weather" to weather?.takeIf { event !is TalkEvent.Revisit }?.toString(),
            "walk" to session?.let { s ->
                mapOf(
                    "minutes" to minutes(s.elapsedMs()),
                    "km" to km(s.distanceM),
                    "spots" to s.visits.joinToString("、") { it.name }.ifEmpty { null },
                )
            },
            "last_walk" to last?.let { w ->
                mapOf(
                    "past_count" to past.size,
                    "when" to daysAgoLabel(w.startedAt, nowMs),
                    "clock" to clock(w.startedAt),
                    "km" to km(w.distanceM),
                    "minutes" to minutes(w.durationMs),
                    "spots" to w.visits.take(3).joinToString("、") { it.name }.ifEmpty { null },
                    "week_count" to past.count { it.startedAt >= weekAgo },
                    "today_number" to (todayWalks + 1).takeIf { todayWalks > 0 },
                )
            },
            "recent" to session?.recentLines?.toList()?.takeIf { it.isNotEmpty() }?.let { mapOf("lines" to it) },
            "location" to session?.takeIf { shareLocation() }?.routeSketch(ROUTE_SKETCH_POINTS)
                ?.takeIf { it.isNotEmpty() }
                ?.let { route ->
                    mapOf(
                        "here" to route.last().let { GuidePrompt.formatLatLon(it.lat, it.lon) },
                        "route" to route.takeIf { it.size > 1 }
                            ?.joinToString(" → ") { GuidePrompt.formatLatLon(it.lat, it.lon).replace(" ", "") },
                    )
                },
        )
    }

    /** Variables for `companion/events/<event>` (documented at the top of each file). */
    private fun eventVars(event: TalkEvent): Map<String, Any?> = when (event) {
        TalkEvent.Start, TalkEvent.Milestone -> emptyMap()
        is TalkEvent.Revisit -> {
            val now = System.currentTimeMillis()
            val last = event.pastVisits.first()
            val todayCount = event.pastVisits.count { daysBetween(it.at, now) == 0 } + 1
            mapOf(
                "name" to event.poi.name,
                "category" to event.poi.category,
                "total_count" to event.pastVisits.size + 1,
                "today_count" to todayCount.takeIf { it > 1 },
                "last_when" to daysAgoLabel(last.at, now),
                "last_remark" to last.remark?.take(200),
            )
        }
        is TalkEvent.Rest -> mapOf("minutes" to event.minutes)
        is TalkEvent.Sunset -> mapOf("minutes" to event.minutes)
        is TalkEvent.WeatherTurn -> event.change.let { c ->
            mapOf(
                "description" to c.description,
                // Forecasts aren't minute-exact: "まもなく" under 10 minutes, else steps of 5.
                "minutes" to c.minutes.takeIf { it >= 10 }?.let { (it + 2) / 5 * 5 },
                "thunder" to (c.kind == WeatherChangeKind.THUNDER),
                "heavy_rain" to (c.kind == WeatherChangeKind.HEAVY_RAIN),
                "rain" to (c.kind == WeatherChangeKind.RAIN),
            )
        }
        is TalkEvent.NearFacility -> event.advice.let { a ->
            mapOf(
                "label" to a.facility.kind.label,
                "name" to a.facility.name,
                // Rounded: GPS isn't accurate enough for "73m" to mean anything.
                "distance_m" to ((a.distanceM + 5) / 10 * 10).coerceAtLeast(10),
                "direction" to event.direction,
                "need_shelter" to (a.need == FacilityNeed.SHELTER),
                "rain_coming" to a.forecast,
                "need_toilet" to (a.need == FacilityNeed.TOILET),
                "need_drink" to (a.need == FacilityNeed.DRINK),
                "need_seat" to (a.need == FacilityNeed.SEAT),
            )
        }
        is TalkEvent.Finish -> mapOf(
            "km" to km(event.walk.distanceM),
            "minutes" to minutes(event.walk.durationMs),
            "spot_count" to event.walk.visits.size,
            "spots" to event.walk.visits.joinToString("、") { it.name }.ifEmpty { null },
        )
    }

    /** Variables for `fallback/companion/<event>`: the event's own plus a few live figures. */
    private fun fallbackVars(event: TalkEvent, session: WalkSession?, eventVars: Map<String, Any?>) = when (event) {
        TalkEvent.Start -> mapOf("greeting" to greeting(Calendar.getInstance()))
        TalkEvent.Milestone -> mapOf(
            "minutes" to minutes(session?.elapsedMs() ?: 0),
            "km" to km(session?.distanceM ?: 0.0),
        )
        else -> eventVars
    }

    companion object {
        private const val TAG = "Companion"
        /** Enough to show the shape of a walk without filling the prompt with numbers. */
        private const val ROUTE_SKETCH_POINTS = 20

        fun km(m: Double) = "%.1f".format(Locale.ROOT, m / 1000)
        fun minutes(ms: Long) = (ms / 60_000).toInt()

        private fun daysAgoLabel(t: Long, now: Long): String =
            daysBetween(t, now).let { if (it == 0) "今日" else "${it}日前" }

        private fun daysBetween(from: Long, to: Long): Int {
            fun dayStart(t: Long) = Calendar.getInstance().apply {
                timeInMillis = t
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            return TimeUnit.MILLISECONDS.toDays(dayStart(to) - dayStart(from) + TimeUnit.HOURS.toMillis(1)).toInt()
        }

        private fun clock(t: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = t }
            return "%d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        }

        private fun formatDateTime(c: Calendar): String {
            val week = "日月火水木金土"[c.get(Calendar.DAY_OF_WEEK) - 1]
            return "%d月%d日（%s）%d:%02d".format(
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), week,
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
            )
        }

        private fun greeting(c: Calendar) = when (c.get(Calendar.HOUR_OF_DAY)) {
            in 4..9 -> "おはようございます"
            in 10..17 -> "こんにちは"
            else -> "こんばんは"
        }
    }
}
