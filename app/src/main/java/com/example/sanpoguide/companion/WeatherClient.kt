package com.example.sanpoguide.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One 15-minute step of the forecast; [at] is the end of the step (epoch ms). */
data class ForecastSlot(val at: Long, val code: Int, val precipitationMm: Double)

data class Weather(
    val description: String,
    val temperatureC: Double,
    val precipitationMm: Double,
    /** Today's sunset at the location (epoch ms), or null if unknown. Not part of [toString]. */
    val sunsetAt: Long? = null,
    /** WMO weather code of [description], or null if unknown. */
    val code: Int? = null,
    /** The next couple of hours in 15-minute steps, oldest first. Not part of [toString]. */
    val forecast: List<ForecastSlot> = emptyList(),
) {
    /** Rain, drizzle, snow or a thunderstorm. */
    val isWet: Boolean get() = precipitationMm > 0 || code?.let(WeatherCodes::isWet) ?: WET_WORDS.any { it in description }

    override fun toString() =
        "$description、気温${"%.0f".format(Locale.ROOT, temperatureC)}℃" +
            if (precipitationMm > 0) "、降水量${precipitationMm}mm/h" else ""

    private companion object {
        // For weather without a code; "霧雨" and "雷雨" contain "雨".
        val WET_WORDS = listOf("雨", "雪")
    }
}

/** WMO weather interpretation codes, as used by Open-Meteo. */
object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "快晴"
        1 -> "晴れ"
        2 -> "晴れ時々くもり"
        3 -> "くもり"
        45, 48 -> "霧"
        51, 53, 55, 56, 57 -> "霧雨"
        61, 63, 66, 80, 81 -> "雨"
        65, 67, 82 -> "強い雨"
        71, 73, 75, 77, 85, 86 -> "雪"
        95, 96, 99 -> "雷雨"
        else -> "不明"
    }

    fun isWet(code: Int) = code in 51..67 || code in 71..77 || code in 80..86 || isThunder(code)
    fun isHeavy(code: Int) = code in setOf(65, 67, 75, 82, 86)
    fun isThunder(code: Int) = code in setOf(95, 96, 99)
}

/** Current weather and a short forecast from Open-Meteo (free, no API key; data CC BY 4.0, open-meteo.com). */
class WeatherClient {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun current(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,precipitation,weather_code" +
            "&minutely_15=precipitation,weather_code&forecast_minutely_15=$FORECAST_STEPS" +
            "&daily=sunset&forecast_days=1&timezone=auto"
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("Open-Meteo error: HTTP ${res.code}")
            val json = JSONObject(res.body!!.string())
            val offset = ZoneOffset.ofTotalSeconds(json.optInt("utc_offset_seconds", 0))
            val current = json.getJSONObject("current")
            val code = current.getInt("weather_code")
            Weather(
                description = WeatherCodes.describe(code),
                temperatureC = current.getDouble("temperature_2m"),
                precipitationMm = current.optDouble("precipitation", 0.0),
                sunsetAt = json.optJSONObject("daily")?.optJSONArray("sunset")?.optString(0)?.let { parseTime(it, offset) },
                code = code,
                forecast = forecastOf(json, offset),
            )
        }
    }

    private fun forecastOf(json: JSONObject, offset: ZoneOffset): List<ForecastSlot> {
        val m = json.optJSONObject("minutely_15") ?: return emptyList()
        val times = m.optJSONArray("time") ?: return emptyList()
        val codes = m.optJSONArray("weather_code") ?: return emptyList()
        val precipitation = m.optJSONArray("precipitation")
        return (0 until times.length()).mapNotNull { i ->
            val at = parseTime(times.optString(i), offset) ?: return@mapNotNull null
            if (codes.isNull(i)) return@mapNotNull null
            ForecastSlot(at, codes.getInt(i), precipitation?.optDouble(i, 0.0)?.takeUnless { it.isNaN() } ?: 0.0)
        }
    }

    /** Times come in the location's local time ("2026-09-28T17:32"); the offset makes them absolute. */
    private fun parseTime(text: String, offset: ZoneOffset): Long? =
        text.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalDateTime.parse(it).toEpochSecond(offset) * 1000 }.getOrNull() }

    private companion object {
        /** 2 hours ahead. */
        const val FORECAST_STEPS = 8
    }
}
