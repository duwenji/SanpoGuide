package com.example.sanpoguide.station.format

/**
 * The channels that ship with the app, as resources under `sanpoguide/channels/{id}/` in the
 * same layout as a third-party package. They go through the same checks, so a mistake in the
 * format shows up here first. The standard channel also fills the slots any other channel
 * leaves empty, in the app and in the review tools alike.
 */
object BuiltInChannels {
    const val RESOURCE_DIR = "sanpoguide/channels"
    const val STANDARD = "standard"

    /** In the order the app lists them. */
    val IDS = listOf(STANDARD, "history", "nature", "quiet")

    /**
     * Every file under [RESOURCE_DIR], one relative path per line. Written by the build
     * (`channelIndex` in station-format/build.gradle.kts), since a jar can't list a folder.
     */
    const val INDEX = "$RESOURCE_DIR/index.txt"

    /** The files of the built-in channel [id], read from this library's resources. */
    fun files(id: String): StationFiles =
        ResourceStationFiles("$RESOURCE_DIR/$id", index().filter { it.startsWith("$id/") }.map { it.removePrefix("$id/") })

    /** Reads and checks the built-in channel in [files]; a rejection means the app's own files are wrong. */
    fun read(id: String, files: StationFiles): StationPackage =
        when (val result = StationValidator.check(files, StationOrigin.BUILT_IN)) {
            is StationCheck.Ok -> result.station.also {
                check(it.manifest.id == id) { "Built-in channel in \"$id\" calls itself \"${it.manifest.id}\"" }
            }
            // Fail loudly rather than run with a broken channel.
            is StationCheck.Rejected -> error("Built-in channel \"$id\" is invalid: ${result.code.json} ${result.detail}")
        }

    /** The standard channel, read from this library's resources. */
    fun standard(): StationPackage = read(STANDARD, files(STANDARD))

    private fun index(): List<String> {
        val stream = BuiltInChannels::class.java.classLoader.getResourceAsStream(INDEX)
            ?: error("No $INDEX: build station-format with Gradle")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readLines() }.filter { it.isNotBlank() }
    }
}

/** A channel folder among the classpath resources, whose files are known from [paths]. */
private class ResourceStationFiles(private val root: String, private val paths: List<String>) : StationFiles {
    override fun list(): List<String> = paths

    override fun read(path: String): ByteArray? =
        ResourceStationFiles::class.java.classLoader.getResourceAsStream("$root/$path")?.use { it.readBytes() }
}
