package io.github.duwenji.sanpoguide.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import io.github.duwenji.sanpoguide.station.BuiltInStations
import io.github.duwenji.sanpoguide.station.format.DirectoryStationFiles
import org.junit.Test
import java.io.File

/**
 * Renders every prompt file under `station-format/src/main/resources/sanpoguide/prompts/` with realistic values, so a typo in
 * a variable name, a missing file or an unused file fails the build. The rendered samples are
 * written to `build/prompt-samples/` for review.
 */
class PromptFilesTest {
    private val root = File("../station-format/src/main/resources/sanpoguide/prompts")
    private val prompts = PromptTemplates { name -> File(root, "$name.md").readText() }

    private val spot = mapOf("name" to "鶴岡八幡宮", "category" to "神社")

    /** System prompts take the channel's slots; the standard channel's are the realistic sample. */
    private val standard = BuiltInStations.load { id -> DirectoryStationFiles(File("../station-format/src/main/resources/sanpoguide/channels/$id")) }
        .first { it.manifest.id == BuiltInStations.STANDARD }

    private val noNeed = mapOf(
        "need_shelter" to false, "rain_coming" to false, "need_toilet" to false, "need_drink" to false, "need_seat" to false,
    )
    private val weatherChangeSamples = listOf(
        mapOf("description" to "雷雨", "minutes" to 25, "thunder" to true, "heavy_rain" to false, "rain" to false),
        mapOf("description" to "霧雨", "minutes" to null, "thunder" to false, "heavy_rain" to false, "rain" to true),
    )

    private val facilitySamples = listOf(
        noNeed + mapOf(
            "label" to "トイレ", "name" to "鎌倉駅東口公衆トイレ", "distance_m" to 80, "direction" to "右手",
            "need_toilet" to true,
        ),
        noNeed + mapOf("label" to "休憩所", "name" to null, "distance_m" to 120, "direction" to null, "need_shelter" to true),
        noNeed + mapOf("label" to "自動販売機", "name" to null, "distance_m" to 40, "direction" to "前方", "need_drink" to true),
        noNeed + mapOf(
            "label" to "休憩所", "name" to "バス停", "distance_m" to 60, "direction" to "左手",
            "need_shelter" to true, "rain_coming" to true,
        ),
    )

    /** Sample values per file; files with two cases cover both the "empty" and "full" shapes. */
    private val samples: Map<String, List<Map<String, Any?>>> = mapOf(
        Prompts.Guide.SYSTEM to listOf(standard.guideSystemVars()),
        Prompts.Guide.USER to listOf(
            spot + mapOf(
                "distance_m" to 45, "inside" to false,
                "tags" to listOf("amenity=place_of_worship", "religion=shinto"),
                "coords" to "35.32580, 139.55620",
            ),
            spot + mapOf("distance_m" to 0, "inside" to true, "tags" to listOf("leisure=park"), "coords" to null),
            spot + mapOf("distance_m" to null, "inside" to false, "tags" to emptyList<String>(), "coords" to null),
        ),
        Prompts.Talk.SYSTEM to listOf(standard.companionSystemVars()),
        Prompts.Talk.STATION_EVENT to listOf(mapOf("text" to "- 歩いてきた道の歴史にひとこと触れる")),
        Prompts.Talk.SITUATION to listOf(
            mapOf(
                "now" to "9月28日（月）14:05", "time_of_day" to "昼", "season" to "秋",
                "weather" to "霧雨、気温19℃",
                "mood" to mapOf("summary" to "秋の昼、雨、寺社の近く"),
                "walk" to mapOf("minutes" to 12, "km" to "0.8", "spots" to "段葛、鶴岡八幡宮"),
                "last_walk" to mapOf(
                    "past_count" to 5, "when" to "今日", "clock" to "13:50", "km" to "1.0", "minutes" to 5,
                    "spots" to "時計台、狛犬", "week_count" to 5, "today_number" to 6,
                ),
                "recent" to mapOf("lines" to listOf("また一緒に歩けてうれしいよ。")),
                "location" to mapOf(
                    "here" to "35.32580, 139.55620",
                    "route" to "35.31940,139.55050 → 35.32270,139.55340 → 35.32580,139.55620",
                ),
            ),
            mapOf(
                "now" to "9月28日（月）14:10", "time_of_day" to "昼", "season" to "秋",
                "weather" to null, "mood" to null,
                "walk" to mapOf("minutes" to 0, "km" to "0.0", "spots" to null),
                "last_walk" to null, "recent" to null,
                "location" to mapOf("here" to "35.31940, 139.55050", "route" to null),
            ),
            mapOf(
                "now" to "9月28日（月）8:00", "time_of_day" to "朝", "season" to "秋",
                "weather" to null, "mood" to null, "walk" to null, "last_walk" to null, "recent" to null,
                "location" to null,
            ),
        ),
        Prompts.event(Prompts.Event.START) to listOf(emptyMap()),
        Prompts.event(Prompts.Event.REVISIT) to listOf(
            spot + mapOf("total_count" to 3, "today_count" to 2, "last_when" to "今日", "last_remark" to "朱色の本宮が…"),
            spot + mapOf("total_count" to 2, "today_count" to null, "last_when" to "3日前", "last_remark" to null),
        ),
        Prompts.event(Prompts.Event.MILESTONE) to listOf(emptyMap()),
        Prompts.event(Prompts.Event.REST) to listOf(mapOf("minutes" to 4)),
        Prompts.event(Prompts.Event.SUNSET) to listOf(mapOf("minutes" to 25)),
        Prompts.event(Prompts.Event.WEATHER_CHANGE) to weatherChangeSamples,
        Prompts.event(Prompts.Event.FACILITY) to facilitySamples,
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
        Prompts.Fallback.companion(Prompts.Event.SUNSET) to listOf(mapOf("minutes" to 25)),
        Prompts.Fallback.companion(Prompts.Event.WEATHER_CHANGE) to weatherChangeSamples,
        Prompts.Fallback.companion(Prompts.Event.FACILITY) to facilitySamples,
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

        val shelter = prompts.render(Prompts.Fallback.companion(Prompts.Event.FACILITY), facilitySamples[1])
        assertEquals("120mほどのところに休憩所があります。\n雨宿りにどうぞ。", shelter)
        val shelterAhead = prompts.render(Prompts.Fallback.companion(Prompts.Event.FACILITY), facilitySamples[3])
        assertEquals("左手60mほどのところに休憩所があります。\n降ってきたら、ここで雨宿りできますよ。", shelterAhead)

        val drizzle = prompts.render(Prompts.Fallback.companion(Prompts.Event.WEATHER_CHANGE), weatherChangeSamples[1])
        assertEquals("まもなく霧雨になりそうです。\n傘の用意をしておきましょうか。", drizzle)
    }
}
