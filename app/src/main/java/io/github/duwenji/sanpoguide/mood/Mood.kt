package io.github.duwenji.sanpoguide.mood

import io.github.duwenji.sanpoguide.companion.WeatherCodes
import java.util.Calendar

enum class TimeOfDay(val label: String) {
    MORNING("朝"), DAY("昼"), EVENING("夕方"), NIGHT("夜"), LATE_NIGHT("深夜");

    val isDark: Boolean get() = this == NIGHT || this == LATE_NIGHT

    companion object {
        fun of(hour: Int) = when (hour) {
            in 4..9 -> MORNING
            in 10..15 -> DAY
            in 16..18 -> EVENING
            in 19..22 -> NIGHT
            else -> LATE_NIGHT
        }

        fun of(c: Calendar) = of(c.get(Calendar.HOUR_OF_DAY))
    }
}

enum class Season(val label: String) {
    SPRING("春"), SUMMER("夏"), AUTUMN("秋"), WINTER("冬");

    companion object {
        /** [month] is 1..12. */
        fun of(month: Int) = when (month) {
            in 3..5 -> SPRING
            in 6..8 -> SUMMER
            in 9..11 -> AUTUMN
            else -> WINTER
        }

        fun of(c: Calendar) = of(c.get(Calendar.MONTH) + 1)
    }
}

/** The weather reduced to what changes the feel of a walk. */
enum class Sky(val label: String) {
    CLEAR("晴れ"), CLOUDY("くもり"), FOG("霧"), RAIN("雨"), SNOW("雪"), THUNDER("雷雨");

    val isWet: Boolean get() = this == RAIN || this == SNOW || this == THUNDER

    companion object {
        /** From a WMO weather code; null when unknown. */
        fun of(code: Int?): Sky? = when {
            code == null -> null
            WeatherCodes.isThunder(code) -> THUNDER
            code in 71..77 || code in 85..86 -> SNOW
            WeatherCodes.isWet(code) -> RAIN
            code == 45 || code == 48 -> FOG
            code == 3 -> CLOUDY
            code in 0..2 -> CLEAR
            else -> null
        }
    }
}

/** What kind of place the user is walking through, guessed from the spots around them. */
enum class Place(val label: String) {
    SHRINE_TEMPLE("寺社"), PARK("公園・緑地"), WATERSIDE("水辺"), HISTORIC("史跡"), TOWN("街なか");

    companion object {
        /** The place a spot category (as shown in the app) suggests; null for ones that say little. */
        fun ofCategory(category: String): Place? = when (category) {
            "神社", "寺院", "教会", "礼拝所" -> SHRINE_TEMPLE
            "公園", "庭園", "自然" -> PARK
            "水辺", "海辺" -> WATERSIDE
            "史跡", "博物館" -> HISTORIC
            else -> null
        }
    }
}

/**
 * The feel of the moment: what the screen colors, the background sound and the companion's
 * tone follow. [sky] is null outside walk mode (no weather then); [place] is null until spots
 * have been searched.
 */
data class Mood(val time: TimeOfDay, val season: Season, val sky: Sky? = null, val place: Place? = null) {
    /** For the companion's prompt, e.g. "秋の夕方、雨、寺社の近く". */
    val summary: String
        get() = listOfNotNull("${season.label}の${time.label}", sky?.label, place?.let { "${it.label}の近く" })
            .joinToString("、")

    companion object {
        fun at(c: Calendar, sky: Sky? = null, place: Place? = null) = Mood(TimeOfDay.of(c), Season.of(c), sky, place)
    }
}
