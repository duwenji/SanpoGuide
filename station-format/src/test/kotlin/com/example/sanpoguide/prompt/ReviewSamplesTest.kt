package com.example.sanpoguide.prompt

import com.example.sanpoguide.station.format.BuiltInChannels
import com.example.sanpoguide.station.format.Slot
import com.example.sanpoguide.station.format.StationPackage
import com.example.sanpoguide.station.format.TalkEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The prompts and channels bundled as resources, as the app and the review tools read them. */
class ReviewSamplesTest {
    private val prompts = Prompts.fromResources()
    private val packages = BuiltInChannels.IDS.associateWith { BuiltInChannels.read(it, BuiltInChannels.files(it)) }
    private val standard = packages.getValue(BuiltInChannels.STANDARD)

    @Test
    fun `every built-in channel loads from the resources`() {
        assertEquals(BuiltInChannels.IDS, packages.values.map { it.manifest.id })
        assertTrue(BuiltInChannels.files(BuiltInChannels.STANDARD).list().contains("channel.json"))
    }

    @Test
    fun `a companion line is the situation, the event and the channel's instructions`() {
        val situation = mapOf(
            "now" to "10月4日（土）8:30", "time_of_day" to "朝", "season" to "秋", "weather" to null, "mood" to null,
            "walk" to null, "last_walk" to null, "recent" to null, "location" to null,
        )
        val plain = StationPrompts.companionUser(prompts, situation, Prompts.Event.START, emptyMap(), null)
        assertEquals(prompts.render(Prompts.Talk.SITUATION, situation) + "\n\n" + prompts.render(Prompts.event(Prompts.Event.START)), plain)

        val extra = StationPrompts.companionUser(prompts, situation, Prompts.Event.START, emptyMap(), "- 名所の由来にひとこと触れる")
        assertEquals(plain + "\n\n" + prompts.render(Prompts.Talk.STATION_EVENT, mapOf("text" to "- 名所の由来にひとこと触れる")), extra)
    }

    @Test
    fun `only the events a channel may add to take its instructions`() {
        assertEquals(
            setOf(Prompts.Event.SUNSET, Prompts.Event.WEATHER_CHANGE, Prompts.Event.FACILITY),
            Prompts.Event.entries.filter { it.stationEvent == null }.toSet(),
        )
        assertNull(StationPrompts.eventInstructions(StationPrompts.slots(standard, standard), TalkEventKind.SPOT))
    }

    @Test
    fun `the standard channel has every scene of the review policy`() {
        val samples = ReviewSamples.build(standard, standard, prompts)
        assertEquals(
            listOf("guide-shrine", "guide-park", "guide-small", "start-morning-sunny", "start-evening-rain", "revisit", "rest", "milestone", "finish"),
            samples.map { it.id },
        )
        samples.forEach { s ->
            listOf(s.system, s.user).forEach { text ->
                assertFalse("${s.id} has a leftover tag:\n$text", "{{" in text || "}}" in text)
                assertFalse("${s.id} has a blank run:\n$text", "\n\n\n" in text)
            }
            // The shared rules close every system prompt, whatever the channel (API-003).
            assertTrue(s.system.endsWith(prompts.render("shared/guard")))
        }
    }

    @Test
    fun `scenes follow the channel's own settings and slots`() {
        val quiet = packages.getValue("quiet")
        val ids = ReviewSamples.build(quiet, standard, prompts).map { it.id }
        assertEquals(listOf("guide-shrine", "guide-park", "guide-small", "start-morning-sunny", "start-evening-rain", "finish"), ids)

        val history = packages.getValue("history")
        val shrine = ReviewSamples.build(history, standard, prompts).first { it.id == "guide-shrine" }
        assertTrue(shrine.system.contains(history.slots.getValue(Slot.GUIDE_PERSONA)))
        assertTrue(shrine.system.contains("300〜450字程度"))
    }

    @Test
    fun `the mood goes in only for channels that follow it`() {
        val start = { pkg: StationPackage -> ReviewSamples.build(pkg, standard, prompts).first { it.id == "start-morning-sunny" }.user }
        val toneless = standard.copy(manifest = standard.manifest.copy(moodTone = false))
        assertTrue(start(standard).contains("秋の朝、晴れ、寺社の近く"))
        assertFalse(start(toneless).contains("秋の朝、晴れ、寺社の近く"))
    }
}
