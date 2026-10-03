package com.example.sanpoguide.station

import com.example.sanpoguide.station.format.BuiltInChannels
import com.example.sanpoguide.station.format.StationFiles

/**
 * The channels that ship with the app. Their files are resources of `:station-format`
 * (`sanpoguide/channels/{id}/`, see [BuiltInChannels]) and go through the same checks as a
 * third-party package, so a mistake in the format shows up here first (and in
 * StationAssetsTest, before it ever reaches a phone).
 */
object BuiltInStations {
    const val STANDARD = BuiltInChannels.STANDARD

    /** In the order the app lists them. */
    val IDS = BuiltInChannels.IDS

    /** Loads every built-in channel; [files] gives the files of one channel by id. */
    fun load(files: (String) -> StationFiles = BuiltInChannels::files): List<Station> {
        val packages = IDS.associateWith { id -> BuiltInChannels.read(id, files(id)) }
        val standard = packages.getValue(STANDARD)
        return IDS.map { id ->
            val pkg = packages.getValue(id)
            Station(pkg, standard, key = "builtin:${pkg.manifest.id}@${pkg.manifest.version}")
        }
    }
}
