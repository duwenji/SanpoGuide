package com.example.sanpoguide.station

import com.example.sanpoguide.prompt.PromptTemplates
import com.example.sanpoguide.prompt.Prompts
import com.example.sanpoguide.station.format.DirectoryStationFiles
import com.example.sanpoguide.settings.TalkLevel
import com.example.sanpoguide.station.format.Slot
import com.example.sanpoguide.station.format.TalkEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The built-in channels under `src/main/assets/channels/` (docs/channel-package-format.md,
 * 確認観点 V-01・V-02・V-09). Rendered system prompts go to `build/prompt-samples/channels/`.
 */
class StationAssetsTest {
    private val prompts = PromptTemplates { name -> File("src/main/assets/prompts/$name.md").readText() }

    // Loading runs the same checks as the app does at start-up and fails on any rejection (V-01).
    private val stations = BuiltInStations.load { id -> DirectoryStationFiles(File("src/main/assets/channels/$id")) }
    private val standard = stations.first { it.manifest.id == BuiltInStations.STANDARD }

    private fun baseline(name: String): String =
        javaClass.getResource("/prompt-baseline/$name.txt")!!.readText().replace("\r\n", "\n").trim()

    @Test
    fun `every built-in channel is in its folder and listed`() {
        val folders = File("src/main/assets/channels").listFiles()!!.filter { it.isDirectory }.map { it.name }.sorted()
        assertEquals(folders, BuiltInStations.IDS.sorted())
        assertEquals(BuiltInStations.IDS, stations.map { it.manifest.id })
    }

    @Test
    fun `the standard channel fills every slot the system prompts need`() {
        listOf(Slot.GUIDE_PERSONA, Slot.GUIDE_FOCUS, Slot.COMPANION_PERSONA, Slot.COMPANION_TOPICS).forEach {
            assertNotNull("standard channel has no $it", standard.slot(it))
        }
    }

    /** The prompts the standard channel builds are the ones the app used before channels, plus the shared rules (V-02・V-09). */
    @Test
    fun `the standard channel keeps the prompts as they were`() {
        val guard = "# 守ること\n" + prompts.render("shared/guard")
        assertEquals(
            baseline("guide_system") + "\n\n" + guard,
            prompts.render(Prompts.Guide.SYSTEM, standard.guideSystemVars()),
        )
        assertEquals(
            baseline("companion_system") + "\n\n" + guard,
            prompts.render(Prompts.Talk.SYSTEM, standard.companionSystemVars()),
        )
    }

    @Test
    fun `the built-in channels differ as designed`() {
        val byId = stations.associateBy { it.id }
        val quiet = byId.getValue("quiet")
        assertEquals(TalkLevel.QUIET, quiet.talkLevel)
        assertEquals(setOf(TalkEventKind.START, TalkEventKind.FINISH), TalkEventKind.entries.filter(quiet::talksOn).toSet())
        assertEquals(setOf("寺院", "神社", "史跡", "博物館"), byId.getValue("history").preferredCategories)
        assertTrue("公園" in byId.getValue("nature").preferredCategories)
        assertTrue(prompts.render(Prompts.Guide.SYSTEM, byId.getValue("history").guideSystemVars()).contains("300〜450字程度"))
        // A channel without slots of its own speaks with the standard channel's voice.
        assertEquals(standard.slot(Slot.GUIDE_PERSONA), quiet.slot(Slot.GUIDE_PERSONA))
    }

    @Test
    fun `the user's changes apply on top of the channel`() {
        val changed = standard.withOverrides(StationOverrides(talkLevel = TalkLevel.CHATTY, moodTone = false))
        assertEquals(TalkLevel.CHATTY, changed.talkLevel)
        assertEquals(TalkLevel.NORMAL, changed.defaultTalkLevel)
        assertFalse(changed.moodTone)
        assertTrue(standard.moodTone)
    }

    @Test
    fun `every channel renders complete system prompts`() {
        val out = File("build/prompt-samples/channels").apply { mkdirs() }
        stations.forEach { station ->
            listOf(
                "guide_system" to prompts.render(Prompts.Guide.SYSTEM, station.guideSystemVars()),
                "companion_system" to prompts.render(Prompts.Talk.SYSTEM, station.companionSystemVars()),
            ).forEach { (name, text) ->
                assertFalse("${station.key} $name has a leftover tag:\n$text", "{{" in text || "}}" in text)
                assertFalse("${station.key} $name has a blank run:\n$text", "\n\n\n" in text)
                // The shared rules close every system prompt, whatever the channel (V-09).
                assertEquals(prompts.render("shared/guard"), text.substringAfterLast("# 守ること\n"))
                File(out, "${station.manifest.id}_$name.txt").writeText(text)
            }
        }
    }
}
