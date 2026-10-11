package io.github.duwenji.sanpoguide.station.format

import java.io.File

/** The files of one channel, by path relative to its root (`channel.json`, `prompts/...`). */
interface StationFiles {
    /** Every file's relative path, with `/` separators. */
    fun list(): List<String>

    /** The file's bytes, or null if there is no such file. */
    fun read(path: String): ByteArray?
}

/** A channel laid out as a folder, as the built-in ones are in the app's sources. */
class DirectoryStationFiles(private val root: File) : StationFiles {
    override fun list(): List<String> = root.walkTopDown().filter { it.isFile }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .toList()

    override fun read(path: String): ByteArray? = File(root, path).takeIf { it.isFile }?.readBytes()
}
