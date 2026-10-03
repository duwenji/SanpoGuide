package com.example.sanpoguide.companion

import com.example.sanpoguide.data.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotChooserTest {
    private fun spot(name: String, category: String, wikipedia: Boolean = false) =
        Poi(name, name, 0.0, 0.0, category, if (wikipedia) mapOf("wikipedia" to "ja:$name") else emptyMap())

    private fun candidate(poi: Poi, distanceM: Float, visitedBefore: Boolean = false) = SpotCandidate(poi, distanceM, visitedBefore)

    private fun pick(
        vararg candidates: SpotCandidate,
        prefer: Set<String> = emptySet(),
        skip: Set<String> = emptySet(),
        newSpots: Boolean = true,
        revisits: Boolean = true,
    ) = SpotChooser.pick(candidates.toList(), prefer, skip, newSpots, revisits)?.poi?.name

    private val park = spot("中央公園", "公園")
    private val shrine = spot("八幡宮", "神社")
    private val documentedPark = spot("源氏山公園", "公園", wikipedia = true)
    private val stone = spot("石碑", "史跡")

    @Test
    fun `without preferences, notable spots come first, then the nearest`() {
        assertEquals("八幡宮", pick(candidate(park, 10f), candidate(shrine, 50f)))
        assertEquals("中央公園", pick(candidate(park, 10f), candidate(stone, 50f)))
    }

    @Test
    fun `a preferred kind comes up first`() {
        // The nature channel prefers parks: a plain park beats a shrine...
        assertEquals("中央公園", pick(candidate(park, 50f), candidate(shrine, 10f), prefer = setOf("公園")))
        // ...and for the history channel a plain historic site ranks with a documented park, so the nearer one wins.
        assertEquals("源氏山公園", pick(candidate(documentedPark, 50f), candidate(stone, 10f)))
        assertEquals("石碑", pick(candidate(documentedPark, 50f), candidate(stone, 10f), prefer = setOf("史跡")))
    }

    @Test
    fun `skipped kinds are never brought up`() {
        assertEquals("八幡宮", pick(candidate(park, 10f), candidate(shrine, 50f), skip = setOf("公園")))
        assertNull(pick(candidate(park, 10f), skip = setOf("公園")))
    }

    @Test
    fun `the channel decides on new spots and revisits separately`() {
        val newPark = candidate(park, 10f)
        val oldShrine = candidate(shrine, 20f, visitedBefore = true)
        assertEquals("中央公園", pick(newPark, oldShrine, revisits = false))
        assertEquals("八幡宮", pick(newPark, oldShrine, newSpots = false))
        // The quiet channel talks about neither.
        assertNull(pick(newPark, oldShrine, newSpots = false, revisits = false))
    }
}
