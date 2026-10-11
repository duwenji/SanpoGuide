package io.github.duwenji.sanpoguide.settings

/** How chatty the walking companion is, for spot talks and small talk alike. */
enum class TalkLevel(
    val label: String,
    /** Minimum gap between two spot talks, so dense streets don't turn into a lecture. */
    val spotGapMs: Long,
    /** Minimum gap between any two things the companion says unprompted. */
    val minGapMs: Long,
    /** Say something after walking this far since the last milestone... */
    val milestoneDistanceM: Double,
    /** ...or after this long, whichever comes first. */
    val milestoneIntervalMs: Long,
    /** Whether to comment when the user stops for a while. */
    val remarkOnRest: Boolean,
) {
    QUIET("控えめ", 5 * MINUTE, 6 * MINUTE, 2000.0, 30 * MINUTE, remarkOnRest = false),
    NORMAL("ふつう", 2 * MINUTE, 3 * MINUTE, 1000.0, 15 * MINUTE, remarkOnRest = true),
    CHATTY("おしゃべり", 1 * MINUTE, 2 * MINUTE, 500.0, 8 * MINUTE, remarkOnRest = true),
}

private const val MINUTE = 60_000L
