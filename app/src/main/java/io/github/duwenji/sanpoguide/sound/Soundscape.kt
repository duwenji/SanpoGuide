package io.github.duwenji.sanpoguide.sound

import io.github.duwenji.sanpoguide.mood.Mood
import io.github.duwenji.sanpoguide.mood.Place
import io.github.duwenji.sanpoguide.mood.Season
import io.github.duwenji.sanpoguide.mood.Sky
import io.github.duwenji.sanpoguide.station.format.SoundChoice

/** Background sounds, synthesized on the phone (see [Voices]). */
enum class Soundscape(val label: String) {
    RAIN("雨音"),
    WAVES("波の音"),
    WIND("風の音"),
    BIRDS("鳥の声"),
    INSECTS("虫の音"),
    TEMPLE("遠くの鐘");

    companion object {
        /** The channel's choice: a fixed sound, or [AUTO][SoundChoice.AUTO] to follow the mood. */
        fun of(choice: SoundChoice, mood: Mood): Soundscape = when (choice) {
            SoundChoice.AUTO -> forMood(mood)
            SoundChoice.RAIN -> RAIN
            SoundChoice.WAVES -> WAVES
            SoundChoice.WIND -> WIND
            SoundChoice.BIRDS -> BIRDS
            SoundChoice.INSECTS -> INSECTS
            SoundChoice.TEMPLE -> TEMPLE
        }

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
