package io.github.duwenji.sanpoguide.prompt

import io.github.duwenji.sanpoguide.station.format.TalkEventKind

/**
 * The app's prompt files, kept as resources under `sanpoguide/prompts/` (see docs/prompts.md).
 * They live in `:station-format` so the channel management system renders the very prompts the
 * app sends. Paths are listed here so code and files can't drift apart silently; a unit test
 * renders every one of them.
 */
object Prompts {
    const val RESOURCE_DIR = "sanpoguide/prompts"

    object Guide {
        const val SYSTEM = "guide/system"
        const val USER = "guide/user"
    }

    object Talk {
        const val SYSTEM = "companion/system"
        const val SITUATION = "companion/situation"
        /** The channel's extra instructions for an event, appended after the event's prompt. */
        const val STATION_EVENT = "companion/station_event"
    }

    /**
     * One file per walk event, in `companion/events/` and `fallback/companion/`.
     *
     * @param stationEvent the event as a channel names it, for the events a channel may add
     *   instructions to; null for the ones that always speak as the app words them
     */
    enum class Event(val file: String, val stationEvent: TalkEventKind?) {
        START("start", TalkEventKind.START),
        REVISIT("revisit", TalkEventKind.REVISIT),
        MILESTONE("milestone", TalkEventKind.MILESTONE),
        REST("rest", TalkEventKind.REST),
        SUNSET("sunset", null),
        WEATHER_CHANGE("weather_change", null),
        FACILITY("facility", null),
        FINISH("finish", TalkEventKind.FINISH),
    }

    fun event(e: Event) = "companion/events/${e.file}"

    object Fallback {
        const val GUIDE = "fallback/guide"
        const val NEARBY = "fallback/nearby"
        fun companion(e: Event) = "fallback/companion/${e.file}"
    }

    object ConnectionTest {
        const val SYSTEM = "connection_test/system"
        const val USER = "connection_test/user"
    }

    /** The prompt files bundled with this library (in the app, inside the APK). */
    fun fromResources() = PromptTemplates { name ->
        val path = "$RESOURCE_DIR/$name.md"
        val stream = Prompts::class.java.classLoader.getResourceAsStream(path)
            ?: throw IllegalArgumentException("No prompt file $path")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
