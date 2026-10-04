package com.example.sanpoguide.station

import com.example.sanpoguide.prompt.StationPrompts
import com.example.sanpoguide.settings.TalkLevel
import com.example.sanpoguide.station.format.GuideLength
import com.example.sanpoguide.station.format.Slot
import com.example.sanpoguide.station.format.SoundChoice
import com.example.sanpoguide.station.format.SpotKind
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
    /** The events to talk on, as a whole (an empty set means none of the six). */
    val events: Set<TalkEventKind>? = null,
    val guideLength: GuideLength? = null,
    val sound: SoundChoice? = null,
    val prefer: Set<SpotKind>? = null,
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
 * @param id how settings name the channel: the manifest's id for a built-in one,
 *   `{provider id}/{channel id}` for a third party's (ids are unique only within a provider).
 * @param source who delivers a third party's channel; null for a built-in one.
 */
class Station(
    private val pkg: StationPackage,
    private val standard: StationPackage,
    val key: String,
    val overrides: StationOverrides = StationOverrides(),
    val id: String = pkg.manifest.id,
    val source: StationSource? = null,
) {
    val manifest: StationManifest get() = pkg.manifest
    val name: String get() = manifest.name

    /** How often the companion speaks up on this channel. */
    val talkLevel: TalkLevel get() = overrides.talkLevel ?: defaultTalkLevel
    val defaultTalkLevel: TalkLevel get() = TalkLevel.valueOf(manifest.talkLevel.name)

    /** Whether the companion talks on [kind]. Weather turns, sunset and amenities aren't asked: they always speak. */
    fun talksOn(kind: TalkEventKind): Boolean = kind in events
    val events: Set<TalkEventKind> get() = overrides.events ?: manifest.events

    /** Spot kinds to bring up first, and ones not to bring up on walks (a kind the user prefers is never skipped). */
    val prefer: Set<SpotKind> get() = overrides.prefer ?: manifest.prefer.toSet()
    val skip: Set<SpotKind> get() = manifest.skip.toSet() - prefer

    /** The same as [prefer] and [skip], as `Poi.category` values. */
    val preferredCategories: Set<String> get() = prefer.map { it.category }.toSet()
    val skippedCategories: Set<String> get() = skip.map { it.category }.toSet()

    val guideLength: GuideLength get() = overrides.guideLength ?: manifest.guideLength

    /** Whether the companion's tone follows the mood. */
    val moodTone: Boolean get() = overrides.moodTone ?: manifest.moodTone

    val sound: SoundChoice get() = overrides.sound ?: manifest.sound

    /** Built-in channels can be adjusted by the user; a third party's are as its publisher made them (ADR-001). */
    val isBuiltIn: Boolean get() = manifest.publisher == null

    fun withOverrides(overrides: StationOverrides) =
        Station(pkg, standard, key, if (isBuiltIn) normalize(overrides) else StationOverrides(), id, source)

    /** [overrides] without the values that equal this channel's own, so only real changes are kept. */
    fun normalize(overrides: StationOverrides): StationOverrides = StationOverrides(
        talkLevel = overrides.talkLevel?.takeIf { it != defaultTalkLevel },
        moodTone = overrides.moodTone?.takeIf { it != manifest.moodTone },
        events = overrides.events?.takeIf { it != manifest.events },
        guideLength = overrides.guideLength?.takeIf { it != manifest.guideLength },
        sound = overrides.sound?.takeIf { it != manifest.sound },
        prefer = overrides.prefer?.takeIf { it != manifest.prefer.toSet() },
    )

    fun slot(slot: Slot): String? = slots(slot)
    private val slots = StationPrompts.slots(pkg, standard)

    /** Variables for `guide/system`. */
    fun guideSystemVars(): Map<String, Any?> = StationPrompts.guideSystemVars(slots, guideLength)

    /** Variables for `companion/system`. */
    fun companionSystemVars(): Map<String, Any?> = StationPrompts.companionSystemVars(slots)

    /** This channel's extra instructions for [kind], appended after the event's own prompt; null if none. */
    fun eventInstructions(kind: TalkEventKind): String? = StationPrompts.eventInstructions(slots, kind)
}

/** Where a third party's channel comes from, for the screens (API-002 `publisherName`, the provider's name). */
data class StationSource(val providerId: String, val providerName: String, val publisherName: String)
