package com.example.sanpoguide.sound

import com.example.sanpoguide.mood.Mood
import com.example.sanpoguide.mood.Place
import com.example.sanpoguide.mood.Season
import com.example.sanpoguide.mood.Sky

/** Background sounds, synthesized on the phone (see [Voices]). */
enum class Soundscape(val label: String) {
    RAIN("雨音"),
    WAVES("波の音"),
    WIND("風の音"),
    BIRDS("鳥の声"),
    INSECTS("虫の音"),
    TEMPLE("遠くの鐘");

    companion object {
        /**
         * The sound for [mood]: the weather first (rain sounds while it rains), then the place,
         * then the time and season. Thunder plays as plain rain: a made-up rumble could hide or
         * be mistaken for the real thing.
         */
        fun forMood(mood: Mood): Soundscape {
            val night = mood.time.isDark
            val insectSeason = mood.season == Season.SUMMER || mood.season == Season.AUTUMN
            return when {
                mood.sky == Sky.RAIN || mood.sky == Sky.THUNDER -> RAIN
                mood.sky == Sky.SNOW -> WIND
                mood.place == Place.WATERSIDE -> WAVES
                night -> if (insectSeason) INSECTS else WIND
                mood.place == Place.SHRINE_TEMPLE -> TEMPLE
                mood.season == Season.WINTER -> WIND
                mood.place == Place.TOWN -> WIND
                else -> BIRDS
            }
        }
    }
}
