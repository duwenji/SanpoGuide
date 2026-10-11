package io.github.duwenji.sanpoguide.station.format

/** `channel.json` of a channel package (API-003). Only the values the format allows can be held. */
data class StationManifest(
    val format: Int,
    val id: String,
    val version: Int,
    /** The publisher's account id (`sg1…`); null for the app's built-in channels. */
    val publisher: String?,
    val name: String,
    val summary: String,
    val lang: String,
    /** Read out as is when the user switches to this channel mid-walk. */
    val greeting: String,
    val talkLevel: TalkLevel,
    /** The walk events this channel talks on. Weather turns, sunset and amenities are not here: they always speak. */
    val events: Set<TalkEventKind>,
    val prefer: List<SpotKind>,
    val skip: List<SpotKind>,
    val guideLength: GuideLength,
    val moodTone: Boolean,
    val sound: SoundChoice,
)

/** A value of an enumerated field, with its spelling in `channel.json`. */
interface JsonValue {
    val json: String
}

internal inline fun <reified E> parseEnum(value: String): E? where E : Enum<E>, E : JsonValue =
    enumValues<E>().firstOrNull { it.json == value }

/** Same three steps as the app's talk levels. */
enum class TalkLevel(override val json: String) : JsonValue { QUIET("quiet"), NORMAL("normal"), CHATTY("chatty") }

/** The events a channel may turn off; all six must be listed in `talk.events`. */
enum class TalkEventKind(override val json: String) : JsonValue {
    SPOT("spot"), REVISIT("revisit"), MILESTONE("milestone"), REST("rest"), START("start"), FINISH("finish"),
}

enum class GuideLength(override val json: String) : JsonValue { SHORT("short"), NORMAL("normal"), LONG("long") }

enum class SoundChoice(override val json: String) : JsonValue {
    AUTO("auto"), RAIN("rain"), WAVES("waves"), WIND("wind"), BIRDS("birds"), INSECTS("insects"), TEMPLE("temple"),
}

/**
 * Spot kinds, matching the app's categories (`OverpassClient.categoryOf`). Kinds marked
 * [onlyWhenPreferred] (eating and shopping) are searched for only while the channel in use
 * prefers them: everywhere else they'd crowd the map and add to the search's load.
 */
enum class SpotKind(override val json: String, val category: String, val onlyWhenPreferred: Boolean = false) : JsonValue {
    SHRINE("shrine", "神社"),
    TEMPLE("temple", "寺院"),
    CHURCH("church", "教会"),
    WORSHIP("worship", "礼拝所"),
    HISTORIC("historic", "史跡"),
    MUSEUM("museum", "博物館"),
    GALLERY("gallery", "ギャラリー"),
    VIEWPOINT("viewpoint", "展望スポット"),
    ARTWORK("artwork", "アート"),
    ATTRACTION("attraction", "名所"),
    PARK("park", "公園"),
    GARDEN("garden", "庭園"),
    BEACH("beach", "海辺"),
    WATER("water", "水辺"),
    NATURE("nature", "自然"),
    RESTAURANT("restaurant", "飲食店", onlyWhenPreferred = true),
    CAFE("cafe", "カフェ", onlyWhenPreferred = true),
    SWEETS("sweets", "菓子・パン", onlyWhenPreferred = true),
    FOOD_SHOP("food_shop", "食品店", onlyWhenPreferred = true),
    MARKET("market", "市場", onlyWhenPreferred = true),
    CRAFTS("crafts", "工芸・土産", onlyWhenPreferred = true),
    SHOP("shop", "専門店", onlyWhenPreferred = true),
}
