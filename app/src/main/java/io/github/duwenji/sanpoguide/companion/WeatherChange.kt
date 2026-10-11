package io.github.duwenji.sanpoguide.companion

import io.github.duwenji.sanpoguide.settings.Threshold
import io.github.duwenji.sanpoguide.settings.Thresholds

/** A turn for the worse, most serious first. Each kind also covers the ones after it. */
enum class WeatherChangeKind {
    THUNDER,
    HEAVY_RAIN,
    /** Rain, drizzle or snow starting. */
    RAIN;

    /** [precipitationMm] and [heavyMm] are 15-minute amounts. */
    fun matches(code: Int, precipitationMm: Double, heavyMm: Double): Boolean = when (this) {
        THUNDER -> WeatherCodes.isThunder(code)
        HEAVY_RAIN -> WeatherCodes.isHeavy(code) || precipitationMm >= heavyMm
        RAIN -> WeatherCodes.isWet(code)
    }
}

/** [minutes] until it starts, roughly; [description] is the forecast weather ("雷雨" etc.). */
data class WeatherChange(val kind: WeatherChangeKind, val minutes: Int, val description: String)

/** Finds a change for the worse coming soon ([Threshold.WEATHER_LOOKAHEAD_MIN]). Pure logic, no Android calls. */
object WeatherChangeDetector {
    private const val SLOT_MS = 15 * 60_000L

    fun detect(weather: Weather, now: Long, limits: Thresholds = Thresholds()): WeatherChange? {
        val lookaheadMs = limits.ms(Threshold.WEATHER_LOOKAHEAD_MIN)
        // The setting is per hour; forecast slots (and Open-Meteo's "current", interval 900s) are 15 minutes.
        val heavyMm = limits[Threshold.HEAVY_RAIN_MM_PER_H] / 4.0
        // Slots not over yet whose weather starts within the lookahead.
        val coming = weather.forecast.filter { it.at > now && it.at - SLOT_MS <= now + lookaheadMs }
        for (kind in WeatherChangeKind.entries) {
            if (isHappening(kind, weather, heavyMm)) continue
            val slot = coming.firstOrNull { kind.matches(it.code, it.precipitationMm, heavyMm) } ?: continue
            // A slot's values cover the 15 minutes up to its time, so it may start at the slot's beginning.
            val minutes = ((slot.at - SLOT_MS - now) / 60_000).toInt().coerceAtLeast(0)
            return WeatherChange(kind, minutes, WeatherCodes.describe(slot.code))
        }
        return null
    }

    /** Already the case now, so nothing to warn about. */
    private fun isHappening(kind: WeatherChangeKind, weather: Weather, heavyMm: Double): Boolean {
        val code = weather.code ?: return kind == WeatherChangeKind.RAIN && weather.isWet
        return kind.matches(code, weather.precipitationMm, heavyMm)
    }
}
