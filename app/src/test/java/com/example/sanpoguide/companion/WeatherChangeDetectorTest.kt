package com.example.sanpoguide.companion

import com.example.sanpoguide.settings.Threshold
import com.example.sanpoguide.settings.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherChangeDetectorTest {
    private val now = 10_000_000_000L
    private val minute = 60_000L

    /** Slots ending 15, 30, ... minutes from now, with the given codes. */
    private fun forecast(vararg codes: Int, precipitation: Double = 0.0) =
        codes.mapIndexed { i, code -> ForecastSlot(now + (i + 1) * 15 * minute, code, precipitation) }

    private fun weather(code: Int, forecast: List<ForecastSlot>, precipitationMm: Double = 0.0) =
        Weather(WeatherCodes.describe(code), 20.0, precipitationMm, code = code, forecast = forecast)

    @Test
    fun `steady weather is no news`() {
        assertNull(WeatherChangeDetector.detect(weather(1, forecast(1, 2, 3, 3)), now))
        assertNull(WeatherChangeDetector.detect(weather(61, forecast(61, 61, 63)), now))
    }

    @Test
    fun `rain starting within the hour`() {
        val change = WeatherChangeDetector.detect(weather(3, forecast(3, 3, 61, 61)), now)!!
        assertEquals(WeatherChangeKind.RAIN, change.kind)
        assertEquals("雨", change.description)
        // The third slot covers 30-45 minutes from now.
        assertEquals(30, change.minutes)
    }

    @Test
    fun `rain beyond the hour is not yet news`() {
        assertNull(WeatherChangeDetector.detect(weather(3, forecast(3, 3, 3, 3, 3, 61)), now))
    }

    @Test
    fun `thunder beats rain, and is news even when it is already raining`() {
        assertEquals(WeatherChangeKind.THUNDER, WeatherChangeDetector.detect(weather(3, forecast(61, 95)), now)!!.kind)
        assertEquals(WeatherChangeKind.THUNDER, WeatherChangeDetector.detect(weather(61, forecast(61, 95)), now)!!.kind)
    }

    @Test
    fun `heavy rain by code or by amount`() {
        assertEquals(WeatherChangeKind.HEAVY_RAIN, WeatherChangeDetector.detect(weather(61, forecast(65)), now)!!.kind)
        assertEquals(
            WeatherChangeKind.HEAVY_RAIN,
            WeatherChangeDetector.detect(weather(61, forecast(63, precipitation = 2.5)), now)!!.kind,
        )
    }

    @Test
    fun `lookahead and heavy rain come from the settings`() {
        val later = weather(3, forecast(3, 3, 3, 3, 3, 61))
        val longer = Thresholds().with(Threshold.WEATHER_LOOKAHEAD_MIN, 90)
        assertEquals(WeatherChangeKind.RAIN, WeatherChangeDetector.detect(later, now, longer)!!.kind)

        // 1.5mm in 15 minutes is 6mm/h: heavy only with a lower setting.
        val moderate = weather(61, forecast(63, precipitation = 1.5))
        assertNull(WeatherChangeDetector.detect(moderate, now))
        val sensitive = Thresholds().with(Threshold.HEAVY_RAIN_MM_PER_H, 5)
        assertEquals(WeatherChangeKind.HEAVY_RAIN, WeatherChangeDetector.detect(moderate, now, sensitive)!!.kind)
    }

    @Test
    fun `past slots are ignored`() {
        val old = listOf(ForecastSlot(now - 5 * minute, 95, 0.0), ForecastSlot(now + 10 * minute, 3, 0.0))
        assertNull(WeatherChangeDetector.detect(weather(3, old), now))
    }

    @Test
    fun `the current slot means any minute now`() {
        val slots = listOf(ForecastSlot(now + 5 * minute, 61, 0.3))
        assertEquals(0, WeatherChangeDetector.detect(weather(3, slots), now)!!.minutes)
    }
}
