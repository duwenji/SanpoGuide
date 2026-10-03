package com.example.sanpoguide.station.format

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** Why a ZIP can't be read as a channel package (API-003 確認の手順 2, `bad_archive`). */
class ArchiveException(message: String) : Exception(message)

/**
 * Unpacks a third-party channel package (API-003 確認の手順 2): a ZIP, stored or deflated, whose
 * entries are plain relative paths. Sizes are counted while reading, so an archive that inflates
 * beyond the limit is cut off rather than unpacked.
 */
object PackageArchive {
    fun open(zip: ByteArray): StationFiles {
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(zip)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val name = entry.name
                    checkPath(name)
                    if (entry.isDirectory) continue
                    if (name in files) throw ArchiveException("$name appears twice")
                    if (files.size >= StationValidator.MAX_FILES) throw ArchiveException("more than ${StationValidator.MAX_FILES} files")
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > StationValidator.MAX_TOTAL_BYTES) {
                            throw ArchiveException("more than ${StationValidator.MAX_TOTAL_BYTES} bytes unpacked")
                        }
                        out.write(buffer, 0, n)
                    }
                    files[name] = out.toByteArray()
                }
            }
        } catch (e: IOException) {
            // Not a ZIP, a broken one, or a compression method other than stored or deflated.
            throw ArchiveException("not a readable ZIP: ${e.message}")
        } catch (e: IllegalArgumentException) {
            // ZipInputStream's answer to entry names that aren't valid UTF-8.
            throw ArchiveException("not a readable ZIP: ${e.message}")
        }
        if (files.isEmpty()) throw ArchiveException("empty archive")
        return MemoryStationFiles(files)
    }

    /** Only plain relative paths: no absolute paths, `..`, backslashes, drive letters or empty segments. */
    private fun checkPath(name: String) {
        val path = name.removeSuffix("/")
        val bad = path.isEmpty() || path.startsWith("/") || '\\' in path || ':' in path ||
            path.split('/').any { it.isEmpty() || it == "." || it == ".." }
        if (bad) throw ArchiveException("bad path \"$name\"")
    }
}

/** Files held in memory, e.g. unpacked from a package. */
class MemoryStationFiles(private val files: Map<String, ByteArray>) : StationFiles {
    override fun list(): List<String> = files.keys.toList()

    override fun read(path: String): ByteArray? = files[path]
}
