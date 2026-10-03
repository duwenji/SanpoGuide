package com.example.sanpoguide.station

import com.example.sanpoguide.settings.TalkLevel
import com.example.sanpoguide.station.format.GuideLength
import com.example.sanpoguide.station.format.Slot
import com.example.sanpoguide.station.format.SoundChoice
import com.example.sanpoguide.station.format.StationManifest
import com.example.sanpoguide.station.format.StationPackage
import com.example.sanpoguide.station.format.TalkEventKind

/**
 * What the user changed in a built-in channel's settings. Only changed values are kept (null =
 * the channel's own value), so a new default in a later version still applies to the rest.
 */
data class StationOverrides(
    val talkLevel: TalkLevel? = null,
    val moodTone: Boolean? = null,
) {
    val isEmpty: Boolean get() = this == StationOverrides()
}

/**
 * A channel ("チャンネル" on screen; see docs/channels.md for the name) ready to use: its settings
 * with the user's changes applied, and the text it puts into the prompts' slots. A slot the
 * channel leaves empty gets the standard channel's text, so every channel renders complete prompts.
 *
 * @param key identifies the channel and its version, e.g. `builtin:standard@1`; it keys caches
 *   and the walk history, so a new version doesn't reuse narrations made for the old one.
 */
class Station(
    private val pkg: StationPackage,
    private val standard: StationPackage,
    val key: String,
    val overrides: StationOverrides = StationOverrides(),
) {
    val manifest: StationManifest get() = pkg.manifest
    val id: String get() = manifest.id
    val name: String get() = manifest.name

    /** How often the companion speaks up on this channel. */
    val talkLevel: TalkLevel get() = overrides.talkLevel ?: defaultTalkLevel
    val defaultTalkLevel: TalkLevel get() = TalkLevel.valueOf(manifest.talkLevel.name)

    /** Whether the companion talks on [kind]. Weather turns, sunset and amenities aren't asked: they always speak. */
    fun talksOn(kind: TalkEventKind): Boolean = kind in manifest.events

    /** Spot categories (`Poi.category`) to bring up first, and ones not to bring up on walks. */
    val preferredCategories: Set<String> = manifest.prefer.map { it.category }.toSet()
    val skippedCategories: Set<String> = manifest.skip.map { it.category }.toSet()

    val guideLength: GuideLength get() = manifest.guideLength

    /** Whether the companion's tone follows the mood. */
    val moodTone: Boolean get() = overrides.moodTone ?: manifest.moodTone

    val sound: SoundChoice get() = manifest.sound

    fun withOverrides(overrides: StationOverrides) = Station(pkg, standard, key, overrides)

    fun slot(slot: Slot): String? = pkg.slots[slot] ?: standard.slots[slot]

    /** Variables for `guide/system`. */
    fun guideSystemVars(): Map<String, Any?> = mapOf(
        "persona" to slot(Slot.GUIDE_PERSONA),
        "length" to lengthText(guideLength),
        "focus" to slot(Slot.GUIDE_FOCUS),
    )

    /** Variables for `companion/system`. */
    fun companionSystemVars(): Map<String, Any?> = mapOf(
        "persona" to slot(Slot.COMPANION_PERSONA),
        "topics" to slot(Slot.COMPANION_TOPICS),
    )

    /** This channel's extra instructions for [kind], appended after the event's own prompt; null if none. */
    fun eventInstructions(kind: TalkEventKind): String? = Slot.forEvent(kind)?.let(::slot)

    companion object {
        /** Wording for the guide's length, as the standard prompt has always put it for [GuideLength.NORMAL]. */
        fun lengthText(length: GuideLength): String = when (length) {
            GuideLength.SHORT -> "100〜150字程度、1〜2段落"
            GuideLength.NORMAL -> "200〜300字程度、2〜3段落"
            GuideLength.LONG -> "300〜450字程度、3〜4段落"
        }
    }
}
