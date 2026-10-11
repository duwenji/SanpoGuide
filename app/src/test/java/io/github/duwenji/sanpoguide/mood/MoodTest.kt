package io.github.duwenji.sanpoguide.mood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoodTest {
    @Test
    fun `time of day and season boundaries`() {
        assertEquals(TimeOfDay.LATE_NIGHT, TimeOfDay.of(3))
        assertEquals(TimeOfDay.MORNING, TimeOfDay.of(4))
        assertEquals(TimeOfDay.DAY, TimeOfDay.of(10))
        assertEquals(TimeOfDay.EVENING, TimeOfDay.of(18))
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(19))
        assertEquals(Season.WINTER, Season.of(2))
        assertEquals(Season.SPRING, Season.of(3))
        assertEquals(Season.AUTUMN, Season.of(11))
    }

    @Test
    fun `sky from weather codes`() {
        assertNull(Sky.of(null))
        assertEquals(Sky.CLEAR, Sky.of(1))
        assertEquals(Sky.CLOUDY, Sky.of(3))
        assertEquals(Sky.FOG, Sky.of(45))
        assertEquals(Sky.RAIN, Sky.of(53)) // drizzle
        assertEquals(Sky.RAIN, Sky.of(81))
        assertEquals(Sky.SNOW, Sky.of(73))
        assertEquals(Sky.SNOW, Sky.of(86))
        assertEquals(Sky.THUNDER, Sky.of(95))
    }

    @Test
    fun `summary leaves out what is unknown`() {
        assertEquals("秋の夕方", Mood(TimeOfDay.EVENING, Season.AUTUMN).summary)
        assertEquals(
            "秋の夕方、雨、寺社の近く",
            Mood(TimeOfDay.EVENING, Season.AUTUMN, Sky.RAIN, Place.SHRINE_TEMPLE).summary,
        )
    }
}

class PlaceGuessTest {
    @Test
    fun `inside an area wins`() {
        val nearby = listOf("公園" to 0f, "神社" to 20f, "神社" to 40f)
        assertEquals(Place.PARK, PlaceGuess.of(nearby))
    }

    @Test
    fun `inside several areas, the grounds of a shrine beat a park`() {
        assertEquals(Place.SHRINE_TEMPLE, PlaceGuess.of(listOf("公園" to 0f, "寺院" to 0f)))
    }

    @Test
    fun `otherwise the most common nearby place`() {
        val nearby = listOf("史跡" to 30f, "神社" to 60f, "寺院" to 90f, "公園" to 100f, "神社" to 400f)
        assertEquals(Place.SHRINE_TEMPLE, PlaceGuess.of(nearby))
    }

    @Test
    fun `a tie goes to the closest`() {
        assertEquals(Place.WATERSIDE, PlaceGuess.of(listOf("公園" to 80f, "海辺" to 40f)))
    }

    @Test
    fun `nothing telling nearby is town`() {
        assertEquals(Place.TOWN, PlaceGuess.of(emptyList()))
        assertEquals(Place.TOWN, PlaceGuess.of(listOf("アート" to 20f, "神社" to 300f)))
    }
}

class PlaceSmootherTest {
    private val minute = 60_000L

    @Test
    fun `the first place is taken at once`() {
        assertEquals(Place.PARK, PlaceSmoother().update(Place.PARK, 0))
    }

    @Test
    fun `a new place is taken only after it lasts`() {
        val s = PlaceSmoother(holdMs = 3 * minute)
        s.update(Place.TOWN, 0)
        assertEquals(Place.TOWN, s.update(Place.PARK, 1 * minute))
        assertEquals(Place.TOWN, s.update(Place.PARK, 3 * minute))
        assertEquals(Place.PARK, s.update(Place.PARK, 4 * minute))
    }

    @Test
    fun `a brief change resets the wait`() {
        val s = PlaceSmoother(holdMs = 3 * minute)
        s.update(Place.TOWN, 0)
        s.update(Place.PARK, 1 * minute)
        s.update(Place.WATERSIDE, 2 * minute)
        assertEquals(Place.TOWN, s.update(Place.PARK, 4 * minute))
        assertEquals(Place.TOWN, s.update(Place.PARK, 6 * minute))
        assertEquals(Place.PARK, s.update(Place.PARK, 7 * minute))
    }

    @Test
    fun `seeing the current place again cancels a pending change`() {
        val s = PlaceSmoother(holdMs = 3 * minute)
        s.update(Place.TOWN, 0)
        s.update(Place.PARK, 1 * minute)
        s.update(Place.TOWN, 2 * minute)
        assertEquals(Place.TOWN, s.update(Place.PARK, 4 * minute))
    }
}
