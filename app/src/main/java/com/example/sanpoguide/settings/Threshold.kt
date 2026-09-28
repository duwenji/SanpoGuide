package com.example.sanpoguide.settings

/**
 * Tunable limits for the companion's safety and amenity remarks, shown in the settings grouped by
 * [group]. [default] is what the app uses unless the user changes it; values are clamped to
 * [min]..[max] when read.
 */
enum class Threshold(
    val group: String,
    val label: String,
    val unit: String,
    val default: Int,
    val min: Int,
    val max: Int,
) {
    WEATHER_LOOKAHEAD_MIN("天気の急変", "何分先までの予報で知らせるか", "分", 60, 15, 120),
    WEATHER_REPEAT_MIN("天気の急変", "同じ種類の予告の間隔", "分", 60, 10, 240),
    HEAVY_RAIN_MM_PER_H("天気の急変", "「強い雨」とする雨量", "mm/h", 8, 1, 50),

    SUNSET_NOTICE_MIN("日の入り", "何分前から知らせるか", "分", 30, 5, 120),
    SUNSET_LATEST_MIN("日の入り", "何分前を過ぎたら知らせないか", "分", 5, 0, 60),

    TOILET_AFTER_MIN("トイレの案内", "歩き始めてから", "分", 20, 0, 180),
    TOILET_RADIUS_M("トイレの案内", "案内する距離", "m", 100, 20, 500),
    TOILET_REPEAT_MIN("トイレの案内", "次の案内までの間隔", "分", 45, 5, 240),

    DRINK_AFTER_MIN("水分補給の案内（暑い日）", "歩き始めてから", "分", 15, 0, 180),
    DRINK_HOT_C("水分補給の案内（暑い日）", "暑いとする気温", "℃", 25, 15, 40),
    DRINK_RADIUS_M("水分補給の案内（暑い日）", "案内する距離", "m", 80, 20, 500),
    DRINK_REPEAT_MIN("水分補給の案内（暑い日）", "次の案内までの間隔", "分", 30, 5, 240),

    SEAT_AFTER_MIN("ひと休みの案内（ベンチ・休憩所）", "歩き始めてから", "分", 40, 0, 240),
    SEAT_RADIUS_M("ひと休みの案内（ベンチ・休憩所）", "案内する距離", "m", 60, 10, 300),
    SEAT_REPEAT_MIN("ひと休みの案内（ベンチ・休憩所）", "次の案内までの間隔", "分", 40, 5, 240),

    SHELTER_RADIUS_M("雨宿りの案内（雨・雪のとき）", "案内する距離", "m", 150, 20, 500),
    SHELTER_REPEAT_MIN("雨宿りの案内（雨・雪のとき）", "次の案内までの間隔", "分", 20, 5, 240);

    fun isValid(value: Int) = value in min..max
}

/** The user's overrides; anything not set uses [Threshold.default]. */
data class Thresholds(val values: Map<Threshold, Int> = emptyMap()) {
    operator fun get(t: Threshold): Int = (values[t] ?: t.default).coerceIn(t.min, t.max)

    /** In milliseconds, for thresholds measured in minutes. */
    fun ms(t: Threshold): Long = get(t) * 60_000L

    fun with(t: Threshold, value: Int) = copy(values = values + (t to value))
}
