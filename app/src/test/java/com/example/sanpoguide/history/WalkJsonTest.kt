package com.example.sanpoguide.history

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkJsonTest {
    private val walk = WalkRecord(
        id = 1L, startedAt = 1L, endedAt = 2_000_000L, distanceM = 1234.5,
        route = listOf(LatLon(35.3, 139.5)),
        visits = listOf(SpotVisit("way/1", "鶴岡八幡宮", "神社", 500L, "朱色の本宮が…", 35.32, 139.55, station = "builtin:history@1")),
        stations = listOf(StationSegment("builtin:standard@1", 1L), StationSegment("builtin:history@1", 400L)),
    )

    @Test
    fun `a walk survives a round trip, channels included`() {
        assertEquals(walk, WalkJson.fromJson(JSONObject(WalkJson.toJson(walk).toString())))
    }

    @Test
    fun `records from before channels still load`() {
        val old = JSONObject(
            """
            {"id": 1, "startedAt": 1, "endedAt": 2, "distanceM": 10.0, "route": [],
             "visits": [{"poiId": "way/1", "name": "段葛", "category": "史跡", "at": 1, "remark": null, "lat": 35.3, "lon": 139.5}]}
            """.trimIndent(),
        )
        val read = WalkJson.fromJson(old)
        assertTrue(read.stations.isEmpty())
        assertNull(read.visits.single().station)
    }
}
