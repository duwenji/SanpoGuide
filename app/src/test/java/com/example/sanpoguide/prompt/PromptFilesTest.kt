package com.example.sanpoguide.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Renders every prompt file under `src/main/assets/prompts/` with realistic values, so a typo in
 * a variable name, a missing file or an unused file fails the build. The rendered samples are
 * written to `build/prompt-samples/` for review.
 */
class PromptFilesTest {
    private val root = File("src/main/assets/prompts")
    private val prompts = PromptTemplates { name -> File(root, "$name.md").readText() }

    private val spot = mapOf("name" to "鶴岡八幡宮", "category" to "神社")

    /** Sample values per file; files with two cases cover both the "empty" and "full" shapes. */
    private val samples: Map<String, List<Map<String, Any?>>> = mapOf(
        Prompts.Guide.SYSTEM to listOf(emptyMap()),
        Prompts.Guide.USER to listOf(
            spot + mapOf(
                "distance_m" to 45, "inside" to false,
                "tags" to listOf("amenity=place_of_worship", "religion=shinto"),
            ),
            spot + mapOf("distance_m" to 0, "inside" to true, "tags" to listOf("leisure=park")),
            spot + mapOf("distance_m" to null, "inside" to false, "tags" to emptyList<String>()),
        ),
        Prompts.Talk.SYSTEM to listOf(emptyMap()),
        Prompts.Talk.SITUATION to listOf(
            mapOf(
                "now" to "9月28日（月）14:05", "time_of_day" to "昼", "season" to "秋",
                "weather" to "霧雨、気温19℃",
                "walk" to mapOf("minutes" to 12, "km" to "0.8", "spots" to "段葛、鶴岡八幡宮"),
                "last_walk" to mapOf(
                    "past_count" to 5, "when" to "今日", "clock" to "13:50", "km" to "1.0", "minutes" to 5,
                    "spots" to "時計台、狛犬", "week_count" to 5, "today_number" to 6,
                ),
                "recent" to mapOf("lines" to listOf("また一緒に歩けてうれしいよ。")),
            ),
            mapOf(
                "now" to "9月28日（月）8:00", "time_of_day" to "朝", "season" to "秋",
                "weather" to null, "walk" to null, "last_walk" to null, "recent" to null,
            ),
        ),
        Prompts.event(Prompts.Event.START) to listOf(emptyMap()),
        Prompts.event(Prompts.Event.REVISIT) to listOf(
            spot + mapOf("total_count" to 3, "today_count" to 2, "last_when" to "今日", "last_remark" to "朱色の本宮が…"),
            spot + mapOf("total_count" to 2, "today_count" to null, "last_when" to "3日前", "last_remark" to null),
        ),
        Prompts.event(Prompts.Event.MILESTONE) to listOf(emptyMap()),
        Prompts.event(Prompts.Event.REST) to listOf(mapOf("minutes" to 4)),
        Prompts.event(Prompts.Event.FINISH) to listOf(
            mapOf("km" to "2.3", "minutes" to 45, "spot_count" to 2, "spots" to "段葛、舞殿"),
            mapOf("km" to "0.4", "minutes" to 6, "spot_count" to 0, "spots" to null),
        ),
        Prompts.Fallback.GUIDE to listOf(
            spot + mapOf("description" to "源氏の氏神", "start_date" to "1063"),
            spot + mapOf("description" to null, "start_date" to null),
        ),
        Prompts.Fallback.NEARBY to listOf(spot),
        Prompts.Fallback.companion(Prompts.Event.START) to listOf(mapOf("greeting" to "こんにちは")),
        Prompts.Fallback.companion(Prompts.Event.REVISIT) to listOf(
            spot + mapOf("total_count" to 2, "today_count" to null, "last_when" to "今日", "last_remark" to null),
        ),
        Prompts.Fallback.companion(Prompts.Event.MILESTONE) to listOf(mapOf("minutes" to 15, "km" to "1.0")),
        Prompts.Fallback.companion(Prompts.Event.REST) to listOf(mapOf("minutes" to 4)),
        Prompts.Fallback.companion(Prompts.Event.FINISH) to listOf(
            mapOf("km" to "2.3", "minutes" to 45, "spot_count" to 2, "spots" to null),
        ),
        Prompts.ConnectionTest.SYSTEM to listOf(emptyMap()),
        Prompts.ConnectionTest.USER to listOf(emptyMap()),
    )

    @Test
    fun `every prompt file is used, and every used file exists`() {
        val onDisk = root.walkTopDown().filter { it.isFile && it.extension == "md" }
            .map { it.relativeTo(root).invariantSeparatorsPath.removeSuffix(".md") }
            .filterNot { it.startsWith("shared/") } // partials, included by other files
            .toSortedSet()
        assertEquals(onDisk, samples.keys.toSortedSet())
    }

    @Test
    fun `every prompt file renders cleanly`() {
        val out = File("build/prompt-samples").apply { mkdirs() }
        samples.forEach { (name, cases) ->
            cases.forEachIndexed { i, vars ->
                val text = prompts.render(name, vars)
                assertTrue("$name is empty", text.isNotBlank())
                assertFalse("$name has a leftover tag:\n$text", "{{" in text || "}}" in text)
                assertFalse("$name has a blank run:\n$text", "\n\n\n" in text)
                File(out, "${name.replace('/', '_')}_${i + 1}.txt").writeText(text)
            }
        }
    }

    @Test
    fun `optional parts disappear cleanly`() {
        val minimal = prompts.render(Prompts.Talk.SITUATION, samples.getValue(Prompts.Talk.SITUATION)[1])
        assertTrue(minimal.contains("初めての散歩"))
        assertFalse(minimal.contains("天気"))
        assertFalse(minimal.contains("最近話したこと"))

        val revisit = prompts.render(Prompts.event(Prompts.Event.REVISIT), samples.getValue(Prompts.event(Prompts.Event.REVISIT))[1])
        assertTrue(revisit.contains("通算2回目で、前回は3日前です。"))
        assertFalse(revisit.contains("今日だけで"))
    }
}
