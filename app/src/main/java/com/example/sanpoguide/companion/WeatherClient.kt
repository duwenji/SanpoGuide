package com.example.sanpoguide.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Weather(val description: String, val temperatureC: Double, val precipitationMm: Double) {
    override fun toString() =
        "$description、気温${"%.0f".format(Locale.ROOT, temperatureC)}℃" +
            if (precipitationMm > 0) "、降水量${precipitationMm}mm/h" else ""
}

/** Current weather from Open-Meteo (free, no API key; data CC BY 4.0, open-meteo.com). */
class WeatherClient {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun current(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,precipitation,weather_code&timezone=auto"
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("Open-Meteo error: HTTP ${res.code}")
            val current = JSONObject(res.body!!.string()).getJSONObject("current")
            Weather(
                description = describe(current.getInt("weather_code")),
                temperatureC = current.getDouble("temperature_2m"),
                precipitationMm = current.optDouble("precipitation", 0.0),
            )
        }
    }

    /** WMO weather interpretation codes. */
    private fun describe(code: Int): String = when (code) {
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
}
