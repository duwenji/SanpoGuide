package io.github.duwenji.sanpoguide.settings

import io.github.duwenji.sanpoguide.station.StationOverrides
import org.junit.Assert.assertEquals
import org.junit.Test

class StationMigrationTest {
    @Test
    fun `default settings move nothing`() {
        assertEquals(StationOverrides(), StationMigration.fromLegacy(talkLevel = "NORMAL", moodEnabled = true))
    }

    @Test
    fun `a changed talk level becomes the standard channel's`() {
        assertEquals(StationOverrides(talkLevel = TalkLevel.QUIET), StationMigration.fromLegacy("QUIET", moodEnabled = true))
        assertEquals(StationOverrides(talkLevel = TalkLevel.CHATTY), StationMigration.fromLegacy("CHATTY", moodEnabled = true))
    }

    @Test
    fun `moods turned off keep the companion's tone plain`() {
        assertEquals(StationOverrides(moodTone = false), StationMigration.fromLegacy("NORMAL", moodEnabled = false))
    }

    @Test
    fun `an unknown stored value is dropped`() {
        assertEquals(StationOverrides(), StationMigration.fromLegacy("LOUD", moodEnabled = true))
        assertEquals(StationOverrides(), StationMigration.fromLegacy(null, moodEnabled = true))
    }
}
