package io.github.duwenji.sanpoguide.prompt

import io.github.duwenji.sanpoguide.station.format.BuiltInChannels
import io.github.duwenji.sanpoguide.station.format.StationPackage
import io.github.duwenji.sanpoguide.station.format.TalkEventKind

/** One scene a reviewer has the AI speak, with the prompts the app would send for it. */
data class ReviewSample(val id: String, val label: String, val system: String, val user: String)

/**
 * The scenes of the review policy (docs/channel-review-policy.md 2.7), rendered for a channel
 * exactly as the app would render them. The channel management system sends these to the AI
 * with the operator's key and shows the replies next to the package.
 *
 * Situations are fixed samples: a walk in Kamakura in autumn, location not shared (the app's
 * default). Events the channel doesn't talk on are left out, since the app never sends them;
 * the guide's scenes are always in, as a spot can be explained on request.
 */
object ReviewSamples {
    fun build(
        pkg: StationPackage,
        standard: StationPackage = BuiltInChannels.standard(),
        prompts: PromptTemplates = Prompts.fromResources(),
    ): List<ReviewSample> {
        val slots = StationPrompts.slots(pkg, standard)
        val manifest = pkg.manifest
        val guideSystem = prompts.render(Prompts.Guide.SYSTEM, StationPrompts.guideSystemVars(slots, manifest.guideLength))
        val companionSystem = prompts.render(Prompts.Talk.SYSTEM, StationPrompts.companionSystemVars(slots))

        fun guide(id: String, label: String, spot: Map<String, Any?>) =
            ReviewSample(id, label, guideSystem, prompts.render(Prompts.Guide.USER, spot))

        fun talk(id: String, label: String, event: Prompts.Event, situation: Map<String, Any?>, eventVars: Map<String, Any?> = emptyMap()) =
            ReviewSample(
                id, label, companionSystem,
                StationPrompts.companionUser(
                    prompts,
                    situation + ("mood" to situation["mood"].takeIf { manifest.moodTone && event != Prompts.Event.REVISIT }),
                    event, eventVars,
                    event.stationEvent?.let { StationPrompts.eventInstructions(slots, it) },
                ),
            )

        val talks = listOf(
            TalkEventKind.START to talk("start-morning-sunny", "散歩の開始（朝・晴れ）", Prompts.Event.START, morningSunny),
            TalkEventKind.START to talk("start-evening-rain", "散歩の開始（夕方・雨）", Prompts.Event.START, eveningRain),
            TalkEventKind.REVISIT to talk(
                "revisit", "再訪したスポットの一言", Prompts.Event.REVISIT, midWalk + ("weather" to null),
                mapOf(
                    "name" to "鶴岡八幡宮", "category" to "神社", "total_count" to 3, "today_count" to null,
                    "last_when" to "3日前", "last_remark" to null,
                ),
            ),
            TalkEventKind.REST to talk("rest", "休憩", Prompts.Event.REST, midWalk, mapOf("minutes" to 4)),
            TalkEventKind.MILESTONE to talk("milestone", "区切り（距離・時間）", Prompts.Event.MILESTONE, midWalk),
            TalkEventKind.FINISH to talk(
                "finish", "散歩の終了", Prompts.Event.FINISH, midWalk + ("walk" to null),
                mapOf("km" to "2.3", "minutes" to 45, "spot_count" to 2, "spots" to "段葛、鶴岡八幡宮"),
            ),
        ).filter { (kind, _) -> kind in manifest.events }.map { it.second }

        return listOf(
            guide("guide-shrine", "初めてのスポットの解説（寺社）", spot("鶴岡八幡宮", "神社", 45, listOf("amenity=place_of_worship", "religion=shinto"))),
            guide("guide-park", "初めてのスポットの解説（公園）", spot("源氏山公園", "公園", 0, listOf("leisure=park"), inside = true)),
            guide("guide-small", "初めてのスポットの解説（情報の少ない小さなスポット）", spot("無名の祠", "神社", 20, listOf("amenity=place_of_worship"))),
        ) + talks
    }

    private fun spot(name: String, category: String, distanceM: Int, tags: List<String>, inside: Boolean = false) = mapOf(
        "name" to name, "category" to category, "distance_m" to distanceM, "inside" to inside, "tags" to tags, "coords" to null,
    )

    private val firstWalk = mapOf(
        "walk" to mapOf("minutes" to 0, "km" to "0.0", "spots" to null),
        "last_walk" to null, "recent" to null, "location" to null,
    )

    private val morningSunny = firstWalk + mapOf(
        "now" to "10月4日（土）8:30", "time_of_day" to "朝", "season" to "秋",
        "weather" to "晴れ、気温18℃", "mood" to mapOf("summary" to "秋の朝、晴れ、寺社の近く"),
    )

    private val eveningRain = firstWalk + mapOf(
        "now" to "10月4日（土）17:10", "time_of_day" to "夕方", "season" to "秋",
        "weather" to "小雨、気温16℃", "mood" to mapOf("summary" to "秋の夕方、雨、住宅地"),
    )

    private val midWalk = mapOf(
        "now" to "10月4日（土）14:05", "time_of_day" to "昼", "season" to "秋",
        "weather" to "くもり、気温20℃", "mood" to mapOf("summary" to "秋の昼、くもり、寺社の近く"),
        "walk" to mapOf("minutes" to 25, "km" to "1.6", "spots" to "段葛、鶴岡八幡宮"),
        "last_walk" to mapOf(
            "past_count" to 5, "when" to "3日前", "clock" to "9:10", "km" to "2.0", "minutes" to 40,
            "spots" to "源氏山公園", "week_count" to 2, "today_number" to null,
        ),
        "recent" to mapOf("lines" to listOf("段葛の参道は、春は桜が見事なんですよ。")),
        "location" to null,
    )
}
