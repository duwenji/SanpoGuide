package com.example.sanpoguide.companion

import com.example.sanpoguide.history.StationSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Switching channels mid-walk (docs/channels.md「選択と切り替え」「記録」). */
class WalkSessionStationTest {
    private val start = 1_000_000L
    private fun session() = WalkSession(startedAt = start).apply { startStation("builtin:standard@1") }

    @Test
    fun `the walk starts on the chosen channel, without a greeting`() {
        val s = session()
        assertEquals(listOf(StationSegment("builtin:standard@1", start)), s.toRecord(start + 1).stations)
        assertNull(s.pendingGreeting)
        assertFalse(s.justSwitched(start))
    }

    @Test
    fun `a switch is recorded and the new channel greets`() {
        val s = session()
        s.switchStation("builtin:history@1", "ここからは、歴史の話を多めにしますね。", now = start + 600_000)
        assertEquals(
            listOf(StationSegment("builtin:standard@1", start), StationSegment("builtin:history@1", start + 600_000)),
            s.toRecord(start + 700_000).stations,
        )
        assertEquals("ここからは、歴史の話を多めにしますね。", s.pendingGreeting)
    }

    @Test
    fun `spot talks and small talk wait a minute after a switch`() {
        val s = session()
        s.switchStation("builtin:quiet@1", "ここからは静かに歩きます。", now = start + 600_000)
        assertTrue(s.justSwitched(start + 600_000 + 59_000))
        assertFalse(s.justSwitched(start + 600_000 + 60_000))
    }

    @Test
    fun `choosing the same channel again is not a switch`() {
        val s = session()
        s.switchStation("builtin:standard@1", "いつもの調子で歩きましょう。", now = start + 600_000)
        assertEquals(1, s.stations.size)
        assertNull(s.pendingGreeting)
    }

    @Test
    fun `going back to an earlier channel is a switch`() {
        val s = session()
        s.switchStation("builtin:history@1", "a", now = start + 1)
        s.switchStation("builtin:standard@1", "b", now = start + 2)
        assertEquals(listOf("builtin:standard@1", "builtin:history@1", "builtin:standard@1"), s.stations.map { it.station })
    }

    @Test
    fun `what was talked about carries over a switch`() {
        val s = session()
        s.talkedAbout += "way/1"
        s.switchStation("builtin:history@1", "a", now = start + 1)
        assertTrue("way/1" in s.talkedAbout)
    }
}
