package com.example.sanpoguide.station.remote

import com.example.sanpoguide.station.Station
import com.example.sanpoguide.station.StationSource
import com.example.sanpoguide.station.format.ChannelList
import com.example.sanpoguide.station.format.Ed25519Verifier
import com.example.sanpoguide.station.format.ListedAs
import com.example.sanpoguide.station.format.ListedChannel
import com.example.sanpoguide.station.format.ProviderDocuments
import com.example.sanpoguide.station.format.ProviderException
import com.example.sanpoguide.station.format.ProviderIds
import com.example.sanpoguide.station.format.ProviderInfo
import com.example.sanpoguide.station.format.StationCheck
import com.example.sanpoguide.station.format.StationPackage
import com.example.sanpoguide.station.format.StationValidator
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/** Fetches a URL, giving up past [maxBytes] (API-002 大きさの上限). Blocking; call it off the main thread. */
fun interface ProviderHttp {
    @Throws(IOException::class)
    fun get(url: String, maxBytes: Int): ByteArray
}

class TooLargeException(url: String) : IOException("$url is too large")

/** Why the user's action can't be done, in their words. */
class ChannelSyncException(message: String) : Exception(message)

/** How a listed channel stands on this device. */
enum class ListedStatus { AVAILABLE, INSTALLED, UPDATE_PENDING, BLOCKED, NEEDS_APP_UPDATE, UNSUPPORTED }

data class ListedView(val channel: ListedChannel, val status: ListedStatus, val installed: InstalledChannel?)

data class ProviderView(
    val record: ProviderRecord,
    /** The name from the discovery document, or the id until one is accepted. */
    val name: String,
    /** The list last accepted, if it is still valid now. */
    val listValid: Boolean,
    val expiresAt: Instant?,
    val channels: List<ListedView>,
    /** Installed channels the list no longer carries. */
    val gone: List<InstalledChannel>,
)

/**
 * Third-party channels (SanpoGuide ADR-001 C-5, API-002 アプリの振る舞い): the providers the user
 * added, their lists, and the packages the user chose. Plain JVM so it is tested without a device;
 * [ThirdPartyChannels] runs it on Android. Not thread-safe: the caller serializes calls.
 *
 * @param appVersion the app's versionCode, against `minAppVersion`.
 * @param allowLocalHttp debug builds may use `http://localhost` and `http://10.0.2.2` (the emulator's host).
 */
class ChannelSync(
    private val store: ChannelStore,
    private val http: ProviderHttp,
    private val verifier: Ed25519Verifier,
    private val appVersion: Int,
    private val now: () -> Instant,
    private val allowLocalHttp: Boolean = false,
) {
    companion object {
        val REFRESH_EVERY: Duration = Duration.ofHours(24)
        const val DISCOVERY_PATH = "/.well-known/sanpo-channels"
        private const val MAX_DISCOVERY_BYTES = 64 * 1024
        private const val MAX_NOTICES = 20
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
    }

    private val docs = ProviderDocuments(verifier)
    var state: ChannelState = store.load()
        private set

    // ---- providers ----

    /**
     * Adds a provider from its URL and the id the user got by another route (API-002: never taken
     * from the server), after its discovery document checks out against that id.
     */
    fun addProvider(url: String, providerId: String) {
        val base = normalizeBase(url)
        val id = providerId.trim()
        if (!ProviderIds.isWellFormed(id)) throw ChannelSyncException("提供元IDの形が正しくありません（sc1 で始まる 35 文字）")
        if (state.providers.any { it.id == id }) throw ChannelSyncException("この提供元はもう登録されています")
        val info = try {
            docs.discovery(String(http.get(base + DISCOVERY_PATH, MAX_DISCOVERY_BYTES), Charsets.UTF_8), id)
        } catch (e: ProviderException) {
            throw ChannelSyncException("提供元の情報を確かめられませんでした（${e.message}）")
        } catch (e: IOException) {
            throw ChannelSyncException("提供元に接続できませんでした（${e.message}）")
        }
        update { it.copy(providers = it.providers + ProviderRecord(id = id, url = base, name = info.name)) }
        refresh(id, unmetered = false)
    }

    fun removeProvider(providerId: String) {
        state.installed.filter { it.provider == providerId }.forEach { store.deletePackage(it.sha256) }
        update { s -> s.copy(providers = s.providers.filter { it.id != providerId }, installed = s.installed.filter { it.provider != providerId }) }
    }

    fun setEnabled(providerId: String, enabled: Boolean) = updateProvider(providerId) { it.copy(enabled = enabled) }

    /** Refreshes the enabled providers not tried for [REFRESH_EVERY] (at start-up and when a walk starts). */
    fun refreshDue(unmetered: Boolean) {
        for (p in state.providers) {
            if (p.enabled && (p.triedAt == null || Duration.between(p.triedAt, now()) >= REFRESH_EVERY)) refresh(p.id, unmetered)
        }
        noticeExpiredLists()
    }

    fun refreshAll(unmetered: Boolean) {
        state.providers.filter { it.enabled }.forEach { refresh(it.id, unmetered) }
        noticeExpiredLists()
    }

    /**
     * Fetches the provider's discovery document and list. A list that fails a check is dropped and
     * the last one kept (until it expires); the reason shows in the settings (API-002 確認に失敗したとき).
     */
    fun refresh(providerId: String, unmetered: Boolean) {
        val record = state.providers.firstOrNull { it.id == providerId } ?: return
        val at = now()
        try {
            val discoveryJson = String(http.get(record.url + DISCOVERY_PATH, MAX_DISCOVERY_BYTES), Charsets.UTF_8)
            val info = docs.discovery(discoveryJson, record.id, minKeysetSeq = record.keysetSeq)
            val listJson = String(http.get(resolve(record.url, info.list), ProviderDocuments.MAX_LIST_BYTES), Charsets.UTF_8)
            val list = try {
                docs.channelList(listJson, info.keyset, at, record.listSeq, record.listSha256)
            } catch (e: ProviderException) {
                if (e.error.code == "expired" && docs.issuedInFuture(listJson, at)) {
                    throw ChannelSyncException("端末の時計がずれているようです。時刻を確かめてください")
                }
                throw e
            }
            updateProvider(record.id) {
                it.copy(
                    name = info.name, discovery = discoveryJson, list = listJson, keysetSeq = info.keyset.seq, listSeq = list.seq,
                    listSha256 = list.sha256, fetchedAt = at, triedAt = at, error = null, expiryNoticed = false,
                )
            }
            apply(record.id, info, list, unmetered)
        } catch (e: ProviderException) {
            updateProvider(record.id) { it.copy(triedAt = at, error = e.message) }
        } catch (e: ChannelSyncException) {
            updateProvider(record.id) { it.copy(triedAt = at, error = e.message) }
        } catch (e: IOException) {
            updateProvider(record.id) { it.copy(triedAt = at, error = "fetch_failed: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    /** What a new list means for the channels on this device: withdrawn ones go, newer versions come. */
    private fun apply(providerId: String, info: ProviderInfo, list: ChannelList, unmetered: Boolean) {
        for (c in state.installed.filter { it.provider == providerId }) {
            val revoked = list.revoked.firstOrNull { it.id == c.id && it.covers(c.version) }
            val listed = list.channels.firstOrNull { it.id == c.id }
            when {
                revoked != null -> {
                    store.deletePackage(c.sha256)
                    update { s -> s.copy(installed = s.installed - c) }
                    notice("「${listed?.name ?: c.name.ifEmpty { c.id }}」は提供元（${info.name}）が取り下げました: ${revoked.reason}")
                }
                listed != null && listed.version > c.version && unmetered -> runCatching { download(providerId, listed, c) }
            }
        }
    }

    // ---- channels ----

    /** Takes a listed channel onto the device: the package is fetched and checked when the user chooses it. */
    fun install(providerId: String, channelId: String) {
        val (_, list) = validList(providerId) ?: throw ChannelSyncException("提供元のリストが有効ではありません。取得し直してください")
        val entry = list.channels.firstOrNull { it.id == channelId } ?: throw ChannelSyncException("リストにないチャンネルです")
        if (entry.pkg.format != 1) throw ChannelSyncException("このアプリでは使えない形式のチャンネルです")
        if (entry.minAppVersion > appVersion) throw ChannelSyncException("このチャンネルを使うには、アプリの更新が必要です")
        val installed = state.installed.firstOrNull { it.provider == providerId && it.id == channelId }
        download(providerId, entry, installed)
    }

    fun uninstall(providerId: String, channelId: String) {
        val c = state.installed.firstOrNull { it.provider == providerId && it.id == channelId } ?: return
        store.deletePackage(c.sha256)
        update { s -> s.copy(installed = s.installed - c) }
    }

    /**
     * Fetches, checks and keeps a package; replaces [previous] only once the new one checks out.
     * A new publisher is accepted only with the list's `publisherChange` from the one on the device (P-9).
     */
    private fun download(providerId: String, entry: ListedChannel, previous: InstalledChannel?) {
        if (previous != null && entry.publisher != previous.publisher) {
            val change = entry.publisherChange
            if (change == null || change.from != previous.publisher) {
                update { s -> s.copy(installed = s.installed.map { if (it == previous) it.copy(blocked = "publisher_changed") else it }) }
                throw ChannelSyncException("配信元が裏付けなく変わったため、新しい版は使いません（publisher_changed）")
            }
        }
        val url = checkedUrl(entry.pkg.url)
        val bytes = try {
            http.get(url, minOf(entry.pkg.size, ProviderDocuments.MAX_PACKAGE_BYTES))
        } catch (e: IOException) {
            throw ChannelSyncException("パッケージを取得できませんでした（${e.message}）")
        }
        if (bytes.size != entry.pkg.size || sha256(bytes) != entry.pkg.sha256) throw ChannelSyncException("パッケージがリストと一致しません（hash_mismatch）")
        val check = StationValidator.checkArchive(bytes, ListedAs(entry.id, entry.version, entry.publisher), verifier)
        if (check is StationCheck.Rejected) throw ChannelSyncException("パッケージの確認に失敗しました（package_rejected: ${check.code.json}）")

        store.writePackage(entry.pkg.sha256, bytes)
        val next = InstalledChannel(providerId, entry.id, entry.version, entry.publisher, entry.pkg.sha256, name = entry.name)
        update { s -> s.copy(installed = s.installed.filter { !(it.provider == providerId && it.id == entry.id) } + next) }
        if (previous != null && previous.sha256 != next.sha256) store.deletePackage(previous.sha256)
        if (previous != null && entry.publisher != previous.publisher) {
            notice("「${entry.name}」の配信元の鍵が移し替えられました: ${entry.publisherChange!!.reason}")
        }
    }

    /**
     * The third-party channels usable now: installed, from an enabled provider whose last list is
     * still valid, still listed and not withdrawn, and whose package still checks out.
     */
    fun stations(standard: StationPackage): List<Station> = state.installed.mapNotNull { c ->
        val provider = state.providers.firstOrNull { it.id == c.provider && it.enabled } ?: return@mapNotNull null
        val (info, list) = validList(c.provider) ?: return@mapNotNull null
        val entry = list.channels.firstOrNull { it.id == c.id } ?: return@mapNotNull null
        if (list.revoked.any { it.id == c.id && it.covers(c.version) }) return@mapNotNull null
        val zip = store.readPackage(c.sha256) ?: return@mapNotNull null
        val pkg = (StationValidator.checkArchive(zip, ListedAs(c.id, c.version, c.publisher), verifier) as? StationCheck.Ok)?.station ?: return@mapNotNull null
        Station(
            pkg, standard, key = "${c.provider}/${c.id}@${c.version}", id = "${c.provider}/${c.id}",
            source = StationSource(provider.id, info.name, entry.publisherName),
        )
    }.sortedBy { it.name }

    /** Each provider with its channels and how they stand, for the settings. */
    fun providers(): List<ProviderView> = state.providers.map { p ->
        val valid = validList(p.id)
        val list = valid?.second
        val mine = state.installed.filter { it.provider == p.id }
        ProviderView(
            record = p,
            name = valid?.first?.name ?: p.name ?: p.id,
            listValid = valid != null,
            expiresAt = list?.expiresAt,
            channels = list?.channels.orEmpty().map { entry ->
                val installed = mine.firstOrNull { it.id == entry.id }
                val status = when {
                    entry.pkg.format != 1 -> ListedStatus.UNSUPPORTED
                    entry.minAppVersion > appVersion -> ListedStatus.NEEDS_APP_UPDATE
                    installed == null -> ListedStatus.AVAILABLE
                    installed.blocked != null -> ListedStatus.BLOCKED
                    installed.version < entry.version -> ListedStatus.UPDATE_PENDING
                    else -> ListedStatus.INSTALLED
                }
                ListedView(entry, status, installed)
            },
            gone = mine.filter { c -> list?.channels?.none { it.id == c.id } ?: false },
        )
    }

    fun dismissNotices() = update { it.copy(notices = emptyList()) }

    // ---- helpers ----

    private val verified = HashMap<String, Pair<ProviderInfo, ChannelList>>()

    /** The provider's last accepted documents, checked again (now: a list that expired since is not valid). */
    private fun validList(providerId: String): Pair<ProviderInfo, ChannelList>? {
        val p = state.providers.firstOrNull { it.id == providerId } ?: return null
        val discovery = p.discovery ?: return null
        val listJson = p.list ?: return null
        val pair = verified.getOrPut(p.id + "\n" + p.listSha256) {
            runCatching {
                val info = docs.discovery(discovery, p.id)
                info to docs.channelList(listJson, info.keyset, now())
            }.getOrNull() ?: return null
        }
        return pair.takeIf { now().isBefore(it.second.expiresAt) }
    }

    /** API-002: an expired list's channels stop from the next walk; say so once, if one was in use. */
    private fun noticeExpiredLists() {
        for (p in state.providers) {
            if (p.list == null || p.expiryNoticed || validList(p.id) != null) continue
            if (state.installed.any { it.provider == p.id }) {
                notice("提供元（${p.name ?: p.id}）のリストの期限が切れたため、そのチャンネルは使えません。取得できるようになると戻ります")
            }
            updateProvider(p.id) { it.copy(expiryNoticed = true) }
        }
    }

    private fun notice(text: String) = update { it.copy(notices = (it.notices + ChannelNotice(now(), text)).takeLast(MAX_NOTICES)) }

    private fun updateProvider(id: String, change: (ProviderRecord) -> ProviderRecord) =
        update { s -> s.copy(providers = s.providers.map { if (it.id == id) change(it) else it }) }

    private fun update(change: (ChannelState) -> ChannelState) {
        state = change(state)
        store.save(state)
    }

    /** `https://…` without a trailing slash; debug builds may also use local `http://`. */
    fun normalizeBase(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        checkedUrl(trimmed)
        return trimmed
    }

    private fun checkedUrl(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull()
        val ok = uri != null && uri.host != null && (uri.scheme == "https" || (allowLocalHttp && uri.scheme == "http" && uri.host in LOCAL_HOSTS))
        if (!ok) throw ChannelSyncException("URL は https:// で始まる必要があります")
        return url
    }

    private fun resolve(base: String, path: String): String = checkedUrl(URI("$base/").resolve(path).toString())

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
