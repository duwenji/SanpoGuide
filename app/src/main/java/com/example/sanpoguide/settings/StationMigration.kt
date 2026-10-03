package com.example.sanpoguide.settings

import com.example.sanpoguide.station.StationOverrides

/** Moves the pre-channel, app-wide talk settings onto the standard channel (docs/channels.md「今の設定の移行」). */
object StationMigration {
    /**
     * @param talkLevel the stored [TalkLevel] name, if any
     * @param moodEnabled the old "雰囲気に合わせる" switch, which covered the screen and the tone alike.
     *   The screen keeps it; the tone becomes the standard channel's.
     * @return only what differs from the standard channel's own values (normal talk, tone on)
     */
    fun fromLegacy(talkLevel: String?, moodEnabled: Boolean): StationOverrides = StationOverrides(
        talkLevel = TalkLevel.entries.firstOrNull { it.name == talkLevel }?.takeIf { it != TalkLevel.NORMAL },
        moodTone = false.takeIf { !moodEnabled },
    )
}
