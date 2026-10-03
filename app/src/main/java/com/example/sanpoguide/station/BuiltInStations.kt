package com.example.sanpoguide.station

import android.content.res.AssetManager
import com.example.sanpoguide.station.format.StationCheck
import com.example.sanpoguide.station.format.StationFiles
import com.example.sanpoguide.station.format.StationOrigin
import com.example.sanpoguide.station.format.StationPackage
import com.example.sanpoguide.station.format.StationValidator

/**
 * The channels that ship with the app, under `assets/channels/{id}/` in the same layout as a
 * third-party package. They go through the same checks, so a mistake in the format shows up
 * here first (and in StationAssetsTest, before it ever reaches a phone).
 */
object BuiltInStations {
    const val ASSET_DIR = "channels"
    const val STANDARD = "standard"

    /** In the order the app lists them. */
    val IDS = listOf(STANDARD)

    /** Loads every built-in channel; [files] gives the files of one channel by id. */
    fun load(files: (String) -> StationFiles): List<Station> {
        val packages = IDS.associateWith { id -> read(id, files(id)) }
        val standard = packages.getValue(STANDARD)
        return IDS.map { id ->
            val pkg = packages.getValue(id)
            Station(pkg, standard, key = "builtin:${pkg.manifest.id}@${pkg.manifest.version}")
        }
    }

    fun fromAssets(assets: AssetManager): List<Station> = load { id -> AssetStationFiles(assets, "$ASSET_DIR/$id") }

    private fun read(id: String, files: StationFiles): StationPackage =
        when (val result = StationValidator.check(files, StationOrigin.BUILT_IN)) {
            is StationCheck.Ok -> result.station.also {
                check(it.manifest.id == id) { "Built-in channel in \"$id\" calls itself \"${it.manifest.id}\"" }
            }
            // The app's own files are wrong: fail loudly rather than run with a broken channel.
            is StationCheck.Rejected -> error("Built-in channel \"$id\" is invalid: ${result.code.json} ${result.detail}")
        }
}

/** A channel folder inside the APK's assets. */
private class AssetStationFiles(private val assets: AssetManager, private val root: String) : StationFiles {
    override fun list(): List<String> = walk("")

    // AssetManager.list() gives the children of a folder and nothing for a file.
    private fun walk(dir: String): List<String> =
        assets.list(if (dir.isEmpty()) root else "$root/$dir").orEmpty().flatMap { name ->
            val path = if (dir.isEmpty()) name else "$dir/$name"
            if (assets.list("$root/$path").isNullOrEmpty()) listOf(path) else walk(path)
        }

    override fun read(path: String): ByteArray? = try {
        assets.open("$root/$path").use { it.readBytes() }
    } catch (e: java.io.IOException) {
        null
    }
}
