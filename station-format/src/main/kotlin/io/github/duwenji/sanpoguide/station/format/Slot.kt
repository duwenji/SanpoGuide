package io.github.duwenji.sanpoguide.station.format

/**
 * The places in the app's prompts where a channel may put its own text (API-003). Everything
 * else in the prompts — the shared rules, safety and privacy — is the app's and can't be changed.
 */
enum class Slot(val path: String, val maxChars: Int) {
    GUIDE_PERSONA("prompts/guide/persona.md", 600),
    GUIDE_FOCUS("prompts/guide/focus.md", 1000),
    COMPANION_PERSONA("prompts/companion/persona.md", 600),
    COMPANION_TOPICS("prompts/companion/topics.md", 1000),
    EVENT_START("prompts/companion/events/start.md", 300),
    EVENT_REVISIT("prompts/companion/events/revisit.md", 300),
    EVENT_MILESTONE("prompts/companion/events/milestone.md", 300),
    EVENT_REST("prompts/companion/events/rest.md", 300),
    EVENT_FINISH("prompts/companion/events/finish.md", 300),
    ;

    companion object {
        /** Keeps the cost of every prompt down, whatever the mix of slots. */
        const val MAX_TOTAL_CHARS = 4000

        fun forEvent(kind: TalkEventKind): Slot? = when (kind) {
            TalkEventKind.START -> EVENT_START
            TalkEventKind.REVISIT -> EVENT_REVISIT
            TalkEventKind.MILESTONE -> EVENT_MILESTONE
            TalkEventKind.REST -> EVENT_REST
            TalkEventKind.FINISH -> EVENT_FINISH
            // A first visit is narrated by the guide prompt, whose slots cover it.
            TalkEventKind.SPOT -> null
        }
    }
}
