package com.example.sanpoguide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleMapTilesTest {
    @Test
    fun parsesSessionWithExpiryInSeconds() {
        val session = GoogleMapTiles.parseSession(
            """{"session":"IgAB","expiry":"1792000000","tileWidth":256,"tileHeight":256,"imageFormat":"png"}""",
        )
        assertEquals("IgAB", session.token)
        assertEquals(1_792_000_000_000L, session.expiresAt)
    }

    @Test
    fun tileUrlCarriesSessionAndKey() {
        val url = GoogleMapTiles.tileUrl(17, 116_000, 51_000, GoogleTileSession("IgAB", 0), "KEY")
        assertEquals("https://tile.googleapis.com/v1/2dtiles/17/116000/51000?session=IgAB&key=KEY", url)
    }

    @Test
    fun refusedKeyBecomesAMessageForTheUser() {
        val e = GoogleMapTiles.errorOf(403, """{"error":{"code":403,"message":"API key not valid."}}""")
        assertTrue(e is GoogleMapsException)
        assertTrue(e.message!!.contains("API key not valid."))
        assertTrue(GoogleMapTiles.errorOf(500, "") !is GoogleMapsException)
    }
}
