package com.example.sanpoguide.history

import android.content.Context
import android.location.Location
import android.util.AtomicFile
import android.util.Log
import com.example.sanpoguide.data.Poi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LatLon(val lat: Double, val lon: Double)

/** One stop at a spot during a walk, with what the companion said there. */
data class SpotVisit(
    val poiId: String,
    val name: String,
    val category: String,
    val at: Long,
    val remark: String?,
    /** Null for records saved before positions were stored. */
    val lat: Double? = null,
    val lon: Double? = null,
    /** The channel in use when the companion spoke here (`Station.key`); null for records from before channels. */
    val station: String? = null,
) {
    /**
     * Whether this visit was to [poi]. OpenStreetMap often maps a place twice (as a point and
     * as an area), and which one a search returns depends on where it was run from, so the
     * id alone isn't stable: the same name close by counts as the same place.
     */
    fun matches(poi: Poi): Boolean {
        if (poiId == poi.id) return true
        if (name != poi.name) return false
        if (lat == null || lon == null) return true
        // A large park's center can be far from the point an earlier visit recorded for it.
        poi.shape?.let { return it.distanceM(lat, lon) <= SAME_PLACE_RADIUS_M }
        val d = FloatArray(1)
        Location.distanceBetween(lat, lon, poi.lat, poi.lon, d)
        return d[0] <= SAME_PLACE_RADIUS_M
    }

    private companion object {
        const val SAME_PLACE_RADIUS_M = 200f
    }
}

data class WalkRecord(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val distanceM: Double,
    val route: List<LatLon>,
    val visits: List<SpotVisit>,
    /** The channels used, in order; empty for records from before channels (which were all the standard one). */
    val stations: List<StationSegment> = emptyList(),
) {
    val durationMs: Long get() = endedAt - startedAt
}

/** From [fromMs] on, the walk was on [station] (`Station.key`, e.g. `builtin:history@1`). */
data class StationSegment(val station: String, val fromMs: Long)

/**
 * The user's walking history, kept only on this device (app-private storage, no backup).
 * Walks are listed newest first.
 */
class HistoryStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "walk_history.json"))
    private val lock = Mutex()

    private val _walks = MutableStateFlow<List<WalkRecord>>(emptyList())
    val walks: StateFlow<List<WalkRecord>> = _walks.asStateFlow()

    suspend fun load() = lock.withLock {
        _walks.value = withContext(Dispatchers.IO) { read() }
    }

    /** Inserts or replaces a walk (walks in progress are saved repeatedly as they grow). */
    suspend fun save(walk: WalkRecord) = lock.withLock {
        val updated = (listOf(walk) + _walks.value.filter { it.id != walk.id })
            .sortedByDescending { it.startedAt }
            .take(MAX_WALKS)
        _walks.value = updated
        withContext(Dispatchers.IO) { write(updated) }
    }

    suspend fun clear() = lock.withLock {
        _walks.value = emptyList()
        withContext(Dispatchers.IO) { file.delete() }
    }

    /** Past visits to a spot (at most one per walk), newest first, optionally excluding the current walk. */
    fun visitsTo(poi: Poi, excludingWalk: Long? = null): List<SpotVisit> =
        _walks.value.asSequence()
            .filter { it.id != excludingWalk }
            .mapNotNull { walk -> walk.visits.firstOrNull { it.matches(poi) } }
            .sortedByDescending { it.at }
            .toList()

    private fun read(): List<WalkRecord> = try {
        if (!file.baseFile.exists()) {
            emptyList()
        } else {
            val array = JSONArray(String(file.readFully(), Charsets.UTF_8))
            (0 until array.length()).map { WalkJson.fromJson(array.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read walk history; starting fresh", e)
        emptyList()
    }

    private fun write(walks: List<WalkRecord>) {
        val json = JSONArray().apply { walks.forEach { put(WalkJson.toJson(it)) } }.toString()
        val out = file.startWrite()
        try {
            out.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.w(TAG, "Could not save walk history", e)
        }
    }

    private companion object {
        const val TAG = "HistoryStore"
        const val MAX_WALKS = 500
    }
}

/** The history file's format, one walk at a time. Fields added later are optional, so old files still load. */
internal object WalkJson {
    fun toJson(w: WalkRecord): JSONObject = JSONObject()
        .put("id", w.id)
        .put("startedAt", w.startedAt)
        .put("endedAt", w.endedAt)
        .put("distanceM", w.distanceM)
        .put("route", JSONArray().apply { w.route.forEach { put(JSONArray().put(it.lat).put(it.lon)) } })
        .put("visits", JSONArray().apply {
            w.visits.forEach { v ->
                put(
                    JSONObject()
                        .put("poiId", v.poiId).put("name", v.name).put("category", v.category)
                        .put("at", v.at).put("remark", v.remark)
                        .put("lat", v.lat).put("lon", v.lon)
                        .put("station", v.station)
                )
            }
        })
        .put("stations", JSONArray().apply {
            w.stations.forEach { put(JSONObject().put("station", it.station).put("from", it.fromMs)) }
        })

    fun fromJson(o: JSONObject): WalkRecord {
        val route = o.optJSONArray("route") ?: JSONArray()
        val visits = o.optJSONArray("visits") ?: JSONArray()
        val stations = o.optJSONArray("stations") ?: JSONArray()
        return WalkRecord(
            id = o.getLong("id"),
            startedAt = o.getLong("startedAt"),
            endedAt = o.getLong("endedAt"),
            distanceM = o.getDouble("distanceM"),
            route = (0 until route.length()).map {
                val p = route.getJSONArray(it)
                LatLon(p.getDouble(0), p.getDouble(1))
            },
            visits = (0 until visits.length()).map {
                val v = visits.getJSONObject(it)
                SpotVisit(
                    poiId = v.getString("poiId"),
                    name = v.getString("name"),
                    category = v.getString("category"),
                    at = v.getLong("at"),
                    remark = if (v.isNull("remark")) null else v.getString("remark"),
                    lat = if (v.has("lat")) v.getDouble("lat") else null,
                    lon = if (v.has("lon")) v.getDouble("lon") else null,
                    station = v.optString("station").ifEmpty { null },
                )
            },
            stations = (0 until stations.length()).map {
                val seg = stations.getJSONObject(it)
                StationSegment(seg.getString("station"), seg.getLong("from"))
            },
        )
    }
}
