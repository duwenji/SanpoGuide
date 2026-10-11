package io.github.duwenji.sanpoguide.settings

import io.github.duwenji.sanpoguide.station.format.JsonValue

/**
 * How the user's channel changes are written to preferences: values in their `channel.json`
 * spelling, sets as comma-separated lists. A value this version doesn't know is dropped.
 */
object OverrideCodec {
    fun <E> encode(values: Set<E>): String where E : Enum<E>, E : JsonValue =
        values.sortedBy { it.ordinal }.joinToString(",") { it.json }

    inline fun <reified E> decodeSet(text: String?): Set<E>? where E : Enum<E>, E : JsonValue =
        text?.split(',')?.filter { it.isNotEmpty() }?.mapNotNull { decode<E>(it) }?.toSet()

    inline fun <reified E> decode(text: String?): E? where E : Enum<E>, E : JsonValue =
        enumValues<E>().firstOrNull { it.json == text }
}
