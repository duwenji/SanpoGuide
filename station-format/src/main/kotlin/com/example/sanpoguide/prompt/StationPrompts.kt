package com.example.sanpoguide.prompt

import com.example.sanpoguide.station.format.GuideLength
import com.example.sanpoguide.station.format.Slot
import com.example.sanpoguide.station.format.StationPackage
import com.example.sanpoguide.station.format.TalkEventKind

/**
 * How a channel's slots go into the app's prompts (API-003 "組み立て方"). The app and the
 * channel management system's review tools both build prompts through here, so a reviewer sees
 * what the app would send.
 */
object StationPrompts {
    /** A channel's slot text, falling back to the standard channel's for any slot it leaves empty. */
    fun slots(pkg: StationPackage, standard: StationPackage): (Slot) -> String? = { pkg.slots[it] ?: standard.slots[it] }

    /** Wording for the guide's length, as the standard prompt has always put it for [GuideLength.NORMAL]. */
    fun lengthText(length: GuideLength): String = when (length) {
        GuideLength.SHORT -> "100〜150字程度、1〜2段落"
        GuideLength.NORMAL -> "200〜300字程度、2〜3段落"
        GuideLength.LONG -> "300〜450字程度、3〜4段落"
    }

    /** Variables for `guide/system`. */
    fun guideSystemVars(slot: (Slot) -> String?, length: GuideLength): Map<String, Any?> = mapOf(
        "persona" to slot(Slot.GUIDE_PERSONA),
        "length" to lengthText(length),
        "focus" to slot(Slot.GUIDE_FOCUS),
    )

    /** Variables for `companion/system`. */
    fun companionSystemVars(slot: (Slot) -> String?): Map<String, Any?> = mapOf(
        "persona" to slot(Slot.COMPANION_PERSONA),
        "topics" to slot(Slot.COMPANION_TOPICS),
    )

    /** The channel's extra instructions for [kind], appended after the event's own prompt; null if none. */
    fun eventInstructions(slot: (Slot) -> String?, kind: TalkEventKind): String? = Slot.forEvent(kind)?.let(slot)

    /**
     * The user prompt for a companion event: the situation, the event's own prompt and, if the
     * channel has any for this event, its extra instructions.
     */
    fun companionUser(
        prompts: PromptTemplates,
        situationVars: Map<String, Any?>,
        event: Prompts.Event,
        eventVars: Map<String, Any?>,
        instructions: String?,
    ): String {
        val extra = instructions?.let { "\n\n" + prompts.render(Prompts.Talk.STATION_EVENT, mapOf("text" to it)) }.orEmpty()
        return prompts.render(Prompts.Talk.SITUATION, situationVars) + "\n\n" + prompts.render(Prompts.event(event), eventVars) + extra
    }
}
