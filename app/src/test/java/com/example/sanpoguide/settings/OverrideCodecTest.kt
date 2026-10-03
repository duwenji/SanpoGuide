package com.example.sanpoguide.settings

import com.example.sanpoguide.station.format.GuideLength
import com.example.sanpoguide.station.format.SpotKind
import com.example.sanpoguide.station.format.TalkEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverrideCodecTest {
    @Test
    fun `sets are stored in channel_json spelling and read back`() {
        val kinds = setOf(SpotKind.WATER, SpotKind.TEMPLE)
        assertEquals("temple,water", OverrideCodec.encode(kinds))
        assertEquals(kinds, OverrideCodec.decodeSet<SpotKind>("temple,water"))
    }

    @Test
    fun `an empty set is kept apart from no setting`() {
        assertEquals("", OverrideCodec.encode(emptySet<TalkEventKind>()))
        assertEquals(emptySet<TalkEventKind>(), OverrideCodec.decodeSet<TalkEventKind>(""))
        assertNull(OverrideCodec.decodeSet<TalkEventKind>(null))
    }

    @Test
    fun `values this version doesn't know are dropped`() {
        assertEquals(setOf(SpotKind.PARK), OverrideCodec.decodeSet<SpotKind>("park,casino"))
        assertNull(OverrideCodec.decode<GuideLength>("epic"))
        assertEquals(GuideLength.LONG, OverrideCodec.decode<GuideLength>("long"))
    }
}
