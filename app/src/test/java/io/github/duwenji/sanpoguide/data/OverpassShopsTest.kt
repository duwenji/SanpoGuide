package io.github.duwenji.sanpoguide.data

import io.github.duwenji.sanpoguide.station.format.SpotKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eating and shopping spots, searched only for channels that prefer them (docs/channel-package-format.md「スポットの種類」). */
class OverpassShopsTest {
    private val client = OverpassClient()

    @Test
    fun `every kind searched only when preferred has a filter`() {
        assertEquals(SpotKind.entries.filter { it.onlyWhenPreferred }.toSet(), OverpassClient.SHOP_FILTERS.keys)
    }

    @Test
    fun `without shops the search is as before`() {
        val query = client.query(35.0, 139.0, 600, emptySet())
        assertFalse(query.contains("restaurant"))
        assertFalse(query.contains("shop"))
    }

    @Test
    fun `shops are asked for nearby, without chains`() {
        val query = client.query(35.0, 139.0, 600, setOf(SpotKind.RESTAURANT, SpotKind.SWEETS))
        assertTrue(query.contains("""nwr(around:400,35.0,139.0)["name"]["amenity"="restaurant"][!"brand"][!"brand:wikidata"];"""))
        assertTrue(query.contains("""["shop"~"^(confectionery|pastry|bakery|chocolate)$"]"""))
        assertFalse(query.contains("cafe"))
        assertFalse(query.contains("convenience"))
        assertTrue(query.trimEnd().endsWith("out tags center 60;"))
    }

    @Test
    fun `shops get their kind, and a building's center as their place`() {
        val found = client.parse(
            """
            {"elements": [
              {"type": "node", "id": 1, "lat": 35.1, "lon": 139.1, "tags": {"name": "鳩サブレー本店", "shop": "confectionery"}},
              {"type": "way", "id": 2, "center": {"lat": 35.2, "lon": 139.2}, "tags": {"name": "小町通りの食堂", "amenity": "restaurant"}},
              {"type": "node", "id": 3, "lat": 35.3, "lon": 139.3, "tags": {"name": "古書店", "shop": "books"}},
              {"type": "node", "id": 4, "lat": 35.4, "lon": 139.4, "tags": {"name": "鶴岡八幡宮", "amenity": "place_of_worship", "religion": "shinto"}}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("菓子・パン", "飲食店", "専門店", "神社"), found.spots.map { it.category })
        val restaurant = found.spots[1]
        assertEquals(35.2, restaurant.lat, 0.0)
        assertEquals(139.2, restaurant.lon, 0.0)
    }
}
