package io.github.duwenji.sanpoguide.companion

import io.github.duwenji.sanpoguide.data.Facility
import io.github.duwenji.sanpoguide.data.FacilityKind
import io.github.duwenji.sanpoguide.settings.Threshold
import io.github.duwenji.sanpoguide.settings.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacilityAdvisorTest {
    private val now = 10_000_000_000L
    private val minute = 60_000L

    private fun facility(id: String, kind: FacilityKind) = Facility(id, kind, 35.0, 139.0, null)

    private val toilet = facility("t", FacilityKind.TOILETS)
    private val vending = facility("v", FacilityKind.VENDING_MACHINE)
    private val shelter = facility("s", FacilityKind.SHELTER)
    private val bench = facility("b", FacilityKind.BENCH)

    private val mild = Weather("晴れ", 20.0, 0.0)
    private val hot = Weather("快晴", 30.0, 0.0)
    private val rain = Weather("霧雨", 18.0, 0.0)

    private fun pick(
        nearby: List<Pair<Facility, Float>>,
        elapsedMin: Long,
        weather: Weather? = mild,
        mentioned: Set<String> = emptySet(),
        lastAt: Map<FacilityNeed, Long> = emptyMap(),
    ) = FacilityAdvisor.pick(nearby, elapsedMin * minute, weather, mentioned, lastAt, now)

    @Test
    fun `nothing is mentioned early in a mild walk`() {
        assertNull(pick(listOf(toilet to 30f, vending to 10f, bench to 10f), elapsedMin = 5))
    }

    @Test
    fun `a close toilet comes up after walking a while`() {
        assertNull(pick(listOf(toilet to 150f), elapsedMin = 30))
        val advice = pick(listOf(toilet to 90f), elapsedMin = 30)!!
        assertEquals(FacilityNeed.TOILET, advice.need)
        assertEquals(90, advice.distanceM)
    }

    @Test
    fun `drinks only when hot`() {
        assertNull(pick(listOf(vending to 20f), elapsedMin = 20, weather = mild))
        assertEquals(FacilityNeed.DRINK, pick(listOf(vending to 20f), elapsedMin = 20, weather = hot)!!.need)
    }

    @Test
    fun `shelter comes first in the rain, from the start of the walk`() {
        val advice = pick(listOf(toilet to 20f, shelter to 140f), elapsedMin = 30, weather = rain)!!
        assertEquals(FacilityNeed.SHELTER, advice.need)
        assertEquals(shelter, advice.facility)
        assertEquals(FacilityNeed.SHELTER, pick(listOf(shelter to 50f), elapsedMin = 0, weather = rain)!!.need)
    }

    @Test
    fun `shelter also when rain is forecast, flagged as a forecast`() {
        val slot = ForecastSlot(now + 30 * minute, 61, 0.5)
        val rainSoon = Weather("くもり", 18.0, 0.0, code = 3, forecast = listOf(slot))
        val advice = pick(listOf(shelter to 100f), elapsedMin = 0, weather = rainSoon)!!
        assertEquals(FacilityNeed.SHELTER, advice.need)
        assertEquals(true, advice.forecast)
        assertEquals(false, pick(listOf(shelter to 100f), elapsedMin = 0, weather = rain)!!.forecast)
        // Rain beyond the lookahead doesn't count yet.
        val later = rainSoon.copy(forecast = listOf(slot.copy(at = now + 3 * 60 * minute)))
        assertNull(pick(listOf(shelter to 100f), elapsedMin = 0, weather = later))
    }

    @Test
    fun `each facility once, and each need spaced out`() {
        assertNull(pick(listOf(toilet to 20f), elapsedMin = 30, mentioned = setOf("t")))
        val other = facility("t2", FacilityKind.TOILETS)
        assertNull(pick(listOf(other to 20f), elapsedMin = 60, lastAt = mapOf(FacilityNeed.TOILET to now - 10 * minute)))
        assertEquals(other, pick(listOf(other to 20f), elapsedMin = 60, lastAt = mapOf(FacilityNeed.TOILET to now - 50 * minute))!!.facility)
    }

    @Test
    fun `the closest matching facility wins`() {
        val near = facility("b2", FacilityKind.BENCH)
        assertEquals(near, pick(listOf(bench to 50f, near to 10f), elapsedMin = 45)!!.facility)
    }

    @Test
    fun `limits come from the settings`() {
        val limits = Thresholds().with(Threshold.TOILET_AFTER_MIN, 5).with(Threshold.TOILET_RADIUS_M, 200)
        val advice = FacilityAdvisor.pick(listOf(toilet to 180f), 10 * minute, mild, emptySet(), emptyMap(), now, limits)
        assertEquals(FacilityNeed.TOILET, advice!!.need)

        val cool = Thresholds().with(Threshold.DRINK_HOT_C, 18)
        assertEquals(
            FacilityNeed.DRINK,
            FacilityAdvisor.pick(listOf(vending to 20f), 20 * minute, mild, emptySet(), emptyMap(), now, cool)!!.need,
        )
    }

    @Test
    fun `direction relative to heading`() {
        assertEquals("前方", FacilityAdvisor.relativeDirection(heading = 350f, bearing = 10f))
        assertEquals("右手", FacilityAdvisor.relativeDirection(heading = 0f, bearing = 90f))
        assertEquals("左手", FacilityAdvisor.relativeDirection(heading = 90f, bearing = 0f))
        assertEquals("後ろ", FacilityAdvisor.relativeDirection(heading = 0f, bearing = -170f))
    }
}
