package io.github.duwenji.sanpoguide.station.remote

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** A provider the user added (API-002 設定画面の提供元), with the documents last accepted from it. */
data class ProviderRecord(
    val id: String,
    /** Where the provider is; `/.well-known/sanpo-channels` is under it. */
    val url: String,
    val enabled: Boolean = true,
    val name: String? = null,
    /** The discovery document and channel list last accepted, as fetched; checked again on use. */
    val discovery: String? = null,
    val list: String? = null,
    val keysetSeq: Int? = null,
    val listSeq: Int? = null,
    val listSha256: String? = null,
    val fetchedAt: Instant? = null,
    val triedAt: Instant? = null,
    /** Why the last try failed (API-002 エラーコード: detail), or null. */
    val error: String? = null,
    /** The user was told the list expired, so it is said once. */
    val expiryNoticed: Boolean = false,
)

/** A third-party channel the user chose to use: its package is kept on the device. */
data class InstalledChannel(
    val provider: String,
    val id: String,
    val version: Int,
    val publisher: String,
    val sha256: String,
    /** Why a newer listed version was not taken (e.g. `publisher_changed`), or null. */
    val blocked: String? = null,
    /** The name when taken, to tell the user about it after the list drops it. */
    val name: String = "",
)

/** A package read from a test ticket (developer mode only), usable until the ticket expires. */
data class TrialChannel(
    val provider: String,
    val id: String,
    val version: Int,
    val publisher: String,
    val sha256: String,
    val name: String,
    val expiresAt: Instant,
)

/** Something the user should hear about once: a channel withdrawn, its publisher's key moved, a list expired. */
data class ChannelNotice(val at: Instant, val text: String)

data class ChannelState(
    val providers: List<ProviderRecord> = emptyList(),
    val installed: List<InstalledChannel> = emptyList(),
    val notices: List<ChannelNotice> = emptyList(),
    val trials: List<TrialChannel> = emptyList(),
)

/** Keeps [ChannelState] in `state.json` and the packages as `pkg/{sha256}.zip` under [dir]. */
class ChannelStore(private val dir: File) {
    private val stateFile = File(dir, "state.json")
    private val packages = File(dir, "pkg")

    fun load(): ChannelState = runCatching { decode(JSONObject(stateFile.readText())) }.getOrDefault(ChannelState())

    fun save(state: ChannelState) {
        dir.mkdirs()
        val tmp = File(dir, "state.json.tmp")
        tmp.writeText(encode(state).toString())
        if (!tmp.renameTo(stateFile)) {
            stateFile.delete()
            tmp.renameTo(stateFile)
        }
    }

    fun readPackage(sha256: String): ByteArray? = File(packages, "$sha256.zip").takeIf { it.isFile }?.readBytes()

    fun writePackage(sha256: String, bytes: ByteArray) {
        packages.mkdirs()
        File(packages, "$sha256.zip").writeBytes(bytes)
    }

    fun deletePackage(sha256: String) {
        File(packages, "$sha256.zip").delete()
    }

    private fun encode(state: ChannelState) = JSONObject()
        .put("providers", JSONArray(state.providers.map { p ->
            JSONObject().put("id", p.id).put("url", p.url).put("enabled", p.enabled)
                .putOpt("name", p.name).putOpt("discovery", p.discovery).putOpt("list", p.list)
                .putOpt("keysetSeq", p.keysetSeq).putOpt("listSeq", p.listSeq).putOpt("listSha256", p.listSha256)
                .putOpt("fetchedAt", p.fetchedAt?.toString()).putOpt("triedAt", p.triedAt?.toString())
                .putOpt("error", p.error).put("expiryNoticed", p.expiryNoticed)
        }))
        .put("installed", JSONArray(state.installed.map { c ->
            JSONObject().put("provider", c.provider).put("id", c.id).put("version", c.version)
                .put("publisher", c.publisher).put("sha256", c.sha256).putOpt("blocked", c.blocked).put("name", c.name)
        }))
        .put("notices", JSONArray(state.notices.map { JSONObject().put("at", it.at.toString()).put("text", it.text) }))
        .put("trials", JSONArray(state.trials.map { t ->
            JSONObject().put("provider", t.provider).put("id", t.id).put("version", t.version).put("publisher", t.publisher)
                .put("sha256", t.sha256).put("name", t.name).put("expiresAt", t.expiresAt.toString())
        }))

    private fun decode(o: JSONObject) = ChannelState(
        providers = o.optJSONArray("providers").objects().map { p ->
            ProviderRecord(
                id = p.getString("id"), url = p.getString("url"), enabled = p.optBoolean("enabled", true),
                name = p.str("name"), discovery = p.str("discovery"), list = p.str("list"),
                keysetSeq = p.int("keysetSeq"), listSeq = p.int("listSeq"), listSha256 = p.str("listSha256"),
                fetchedAt = p.str("fetchedAt")?.let(Instant::parse), triedAt = p.str("triedAt")?.let(Instant::parse),
                error = p.str("error"), expiryNoticed = p.optBoolean("expiryNoticed"),
            )
        },
        installed = o.optJSONArray("installed").objects().map { c ->
            InstalledChannel(c.getString("provider"), c.getString("id"), c.getInt("version"), c.getString("publisher"), c.getString("sha256"), c.str("blocked"), c.optString("name"))
        },
        notices = o.optJSONArray("notices").objects().map { ChannelNotice(Instant.parse(it.getString("at")), it.getString("text")) },
        trials = o.optJSONArray("trials").objects().map { t ->
            TrialChannel(t.getString("provider"), t.getString("id"), t.getInt("version"), t.getString("publisher"), t.getString("sha256"), t.getString("name"), Instant.parse(t.getString("expiresAt")))
        },
    )
}

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

private fun JSONObject.int(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
