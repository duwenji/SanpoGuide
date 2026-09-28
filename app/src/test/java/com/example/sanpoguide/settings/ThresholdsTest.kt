package com.example.sanpoguide.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdsTest {
    @Test
    fun `defaults unless overridden`() {
        val t = Thresholds().with(Threshold.TOILET_RADIUS_M, 200)
        assertEquals(200, t[Threshold.TOILET_RADIUS_M])
        assertEquals(Threshold.TOILET_AFTER_MIN.default, t[Threshold.TOILET_AFTER_MIN])
        assertEquals(20 * 60_000L, t.ms(Threshold.TOILET_AFTER_MIN))
    }

    @Test
    fun `out-of-range values are clamped when read`() {
        val t = Thresholds().with(Threshold.DRINK_HOT_C, 99).with(Threshold.SEAT_RADIUS_M, -1)
        assertEquals(Threshold.DRINK_HOT_C.max, t[Threshold.DRINK_HOT_C])
        assertEquals(Threshold.SEAT_RADIUS_M.min, t[Threshold.SEAT_RADIUS_M])
    }

    @Test
    fun `every default is within its range`() {
        Threshold.entries.forEach { assertTrue(it.name, it.isValid(it.default)) }
    }
}
