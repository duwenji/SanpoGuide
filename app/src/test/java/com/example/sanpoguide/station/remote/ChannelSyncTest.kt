package com.example.sanpoguide.station.remote

import com.example.sanpoguide.station.BuiltInStations
import com.example.sanpoguide.station.format.AccountIds
import com.example.sanpoguide.station.format.ProviderIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Third-party channels end to end on the JVM (API-002 アプリの振る舞い): a provider and publishers
 * sign as the channel management system and its publishers do, and Tink checks the signatures.
 */
class ChannelSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val base = "https://channels.example"
    private val start = Instant.parse("2026-10-05T00:00:00Z")
    private var clock = start
    private val files = HashMap<String, ByteArray>()
    private val provider = TestProvider()
    private val alice = TestPublisher()
    private val bob = TestPublisher()
    private val standard = BuiltInStations.standardPackage()
    private lateinit var sync: ChannelSync

    private val http = ProviderHttp { url, max ->
        val bytes = files[url] ?: throw IOException("HTTP 404")
        if (bytes.size > max) throw TooLargeException(url)
        bytes
    }

    private fun newSync() = ChannelSync(ChannelStore(folder.root), http, TinkEd25519, appVersion = 1, now = { clock })

    @Before
    fun setUp() {
        files["$base/.well-known/sanpo-channels"] = provider.discovery().toByteArray()
        sync = newSync()
    }

    /** Publishes [packages] (and [revoked]) as list [seq]; returns the list entries. */
    private fun publish(seq: Int, vararg packages: Pair<TestPublisher, Int>, revoked: JSONArray = JSONArray(), change: JSONObject? = null, issuedAt: Instant = clock) {
        val channels = packages.map { (publisher, version) ->
            val zip = publisher.packageZip("kamakura-history", version)
            val sha = sha256(zip)
            files["$base/pkg/$sha.zip"] = zip
            JSONObject()
                .put("id", "kamakura-history").put("version", version).put("publisher", publisher.accountId).put("publisherName", "鎌倉歴史散歩の会")
                .put("name", "鎌倉歴史散歩").put("summary", "寺社と武士の歴史").put("lang", JSONArray(listOf("ja")))
                .put("icon", JSONObject().put("url", "$base/icons/x.png").put("sha256", "0".repeat(64)))
                .put("package", JSONObject().put("url", "$base/pkg/$sha.zip").put("sha256", sha).put("size", zip.size).put("format", 1))
                .put("minAppVersion", 1).put("approvedAt", issuedAt.toString())
                .apply { if (change != null) put("publisherChange", change) }
        }
        files["$base/v1/channels.json"] = provider.list(seq, JSONArray(channels), revoked, issuedAt, issuedAt.plus(Duration.ofDays(14))).toByteArray()
    }

    private fun useKamakura() {
        sync.addProvider(base, provider.id)
        sync.install(provider.id, "kamakura-history")
    }

    @Test
    fun `a channel from an added provider becomes usable once taken onto the device`() {
        publish(1, alice to 1)
        sync.addProvider("$base/", provider.id)
        val view = sync.providers().single()
        assertEquals("検証用の提供元", view.name)
        assertEquals(ListedStatus.AVAILABLE, view.channels.single().status)
        assertTrue(sync.stations(standard).isEmpty())

        sync.install(provider.id, "kamakura-history")
        val station = sync.stations(standard).single()
        assertEquals("${provider.id}/kamakura-history", station.id)
        assertEquals("${provider.id}/kamakura-history@1", station.key)
        assertEquals("鎌倉歴史散歩の会", station.source?.publisherName)
        assertFalse(station.isBuiltIn)
        // Slots the channel leaves empty come from the standard channel.
        assertNotNull(station.companionSystemVars())
        assertEquals(ListedStatus.INSTALLED, sync.providers().single().channels.single().status)

        // Kept across restarts.
        assertEquals(1, newSync().stations(standard).size)
    }

    @Test
    fun `a provider is added only with the id the user got elsewhere, over https`() {
        publish(1, alice to 1)
        assertThrows(ChannelSyncException::class.java) { sync.addProvider(base, "sc1" + "a".repeat(32)) }
        assertThrows(ChannelSyncException::class.java) { sync.addProvider("http://channels.example", provider.id) }
        assertThrows(ChannelSyncException::class.java) { sync.addProvider(base, "not-an-id") }
        assertTrue(sync.state.providers.isEmpty())
        // Debug builds may use the emulator's host.
        files["http://10.0.2.2:8080/.well-known/sanpo-channels"] = provider.discovery().toByteArray()
        val debug = ChannelSync(ChannelStore(folder.newFolder()), http, TinkEd25519, 1, { clock }, allowLocalHttp = true)
        debug.addProvider("http://10.0.2.2:8080", provider.id)
        assertEquals(1, debug.state.providers.size)
    }

    @Test
    fun `a withdrawn channel is removed with its reason, and a bad list leaves the last good one in use`() {
        publish(1, alice to 1)
        useKamakura()

        // A list that fails its check is dropped; the channel stays usable on the last list.
        files["$base/v1/channels.json"] = files.getValue("$base/v1/channels.json").let { String(it).replace("\"payload\":\"ey", "\"payload\":\"ex").toByteArray() }
        clock = clock.plus(Duration.ofDays(1))
        sync.refreshDue(unmetered = true)
        assertTrue(sync.state.providers.single().error!!.startsWith("bad_"))
        assertEquals(1, sync.stations(standard).size)

        publish(2, revoked = JSONArray().put(JSONObject().put("id", "kamakura-history").put("reason", "審査基準に反する内容").put("revokedAt", clock.toString())))
        sync.refreshAll(unmetered = false)
        assertTrue(sync.stations(standard).isEmpty())
        assertTrue(sync.state.installed.isEmpty())
        assertEquals("「鎌倉歴史散歩」は提供元（検証用の提供元）が取り下げました: 審査基準に反する内容", sync.state.notices.single().text)
    }

    @Test
    fun `an older list is refused as a rollback`() {
        publish(5, alice to 1)
        useKamakura()
        publish(4, alice to 2)
        sync.refreshAll(unmetered = true)
        assertTrue(sync.state.providers.single().error!!.startsWith("rollback"))
        assertEquals(1, sync.state.installed.single().version)
    }

    @Test
    fun `an expired list stops its channels and is said once`() {
        publish(1, alice to 1)
        useKamakura()
        files.remove("$base/v1/channels.json")
        clock = clock.plus(Duration.ofDays(15))
        sync.refreshDue(unmetered = true)
        assertTrue(sync.state.providers.single().error!!.startsWith("fetch_failed"))
        assertTrue(sync.stations(standard).isEmpty())
        assertFalse(sync.providers().single().listValid)
        sync.refreshAll(unmetered = true)
        assertEquals(1, sync.state.notices.size)
        // Back once the provider is reachable again.
        publish(2, alice to 1)
        sync.refreshAll(unmetered = true)
        assertEquals(1, sync.stations(standard).size)
    }

    @Test
    fun `a newer version comes on an unmetered network, or when the user asks`() {
        publish(1, alice to 1)
        useKamakura()
        publish(2, alice to 2)
        sync.refreshAll(unmetered = false)
        assertEquals(ListedStatus.UPDATE_PENDING, sync.providers().single().channels.single().status)
        assertEquals(1, sync.stations(standard).single().manifest.version)
        sync.refreshAll(unmetered = true)
        assertEquals(2, sync.stations(standard).single().manifest.version)

        publish(3, alice to 3)
        sync.refreshAll(unmetered = false)
        sync.install(provider.id, "kamakura-history")
        assertEquals("${provider.id}/kamakura-history@3", sync.stations(standard).single().key)
    }

    @Test
    fun `a new publisher is taken only with publisherChange from the one on the device`() {
        publish(1, alice to 1)
        useKamakura()

        // Without the provider's word for it: the old version stays (publisher_changed).
        publish(2, bob to 2)
        sync.refreshAll(unmetered = true)
        assertThrows(ChannelSyncException::class.java) { sync.install(provider.id, "kamakura-history") }
        assertEquals(ListedStatus.BLOCKED, sync.providers().single().channels.single().status)
        assertEquals(1, sync.stations(standard).single().manifest.version)

        // With it: taken, and the user is told why.
        val change = JSONObject().put("from", alice.accountId).put("at", clock.toString()).put("reason", "配信元の鍵の紛失")
        publish(3, bob to 3, change = change)
        sync.refreshAll(unmetered = false)
        sync.install(provider.id, "kamakura-history")
        assertEquals(bob.accountId, sync.state.installed.single().publisher)
        assertNull(sync.state.installed.single().blocked)
        assertTrue(sync.state.notices.last().text.contains("配信元の鍵の紛失"))
    }

    @Test
    fun `packages that differ from the list, or need a newer app, are not taken`() {
        publish(1, alice to 1)
        sync.addProvider(base, provider.id)
        val url = files.keys.first { it.contains("/pkg/") }
        files[url] = files.getValue(url).copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }
        assertTrue(assertThrows(ChannelSyncException::class.java) { sync.install(provider.id, "kamakura-history") }.message!!.contains("hash_mismatch"))

        val old = ChannelSync(ChannelStore(folder.newFolder()), http, TinkEd25519, appVersion = 0, now = { clock })
        publish(2, alice to 1)
        old.addProvider(base, provider.id)
        assertEquals(ListedStatus.NEEDS_APP_UPDATE, old.providers().single().channels.single().status)
        assertThrows(ChannelSyncException::class.java) { old.install(provider.id, "kamakura-history") }
    }

    @Test
    fun `a disabled or removed provider's channels are not used`() {
        publish(1, alice to 1)
        useKamakura()
        sync.setEnabled(provider.id, false)
        assertTrue(sync.stations(standard).isEmpty())
        sync.setEnabled(provider.id, true)
        assertEquals(1, sync.stations(standard).size)
        sync.removeProvider(provider.id)
        assertTrue(sync.stations(standard).isEmpty())
        assertTrue(folder.root.resolve("pkg").listFiles().orEmpty().isEmpty())
    }
}

private val b64 = Base64.getUrlEncoder().withoutPadding()

private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun KeyPair.raw(): ByteArray = public.encoded.takeLast(32).toByteArray()

private fun KeyPair.sign(message: ByteArray): ByteArray = Signature.getInstance("Ed25519").run {
    initSign(private)
    update(message)
    sign()
}

private fun ed25519() = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

private fun signed(content: JSONObject, keyId: String, key: KeyPair): JSONObject {
    val bytes = content.toString().toByteArray()
    return JSONObject().put("payload", b64.encodeToString(bytes)).put("keyId", keyId).put("sig", b64.encodeToString(key.sign(bytes)))
}

/** A channel provider as the channel management system signs (API-002): a root key and one signing key. */
private class TestProvider {
    private val root = ed25519()
    private val signing = ed25519()
    val id: String = ProviderIds.of(root.raw())

    fun discovery(): String {
        val keyset = JSONObject().put("type", "keyset").put("provider", id).put("seq", 1).put("issuedAt", "2026-10-01T00:00:00Z")
            .put("keys", JSONArray().put(JSONObject().put("keyId", "k-1").put("publicKey", b64.encodeToString(signing.raw())).put("notBefore", "2026-10-01T00:00:00Z").put("notAfter", "2027-04-01T00:00:00Z")))
            .put("revokedKeys", JSONArray())
        return JSONObject().put("provider", id).put("name", "検証用の提供元").put("versions", JSONArray(listOf("v1"))).put("list", "/v1/channels.json")
            .put("rootKey", b64.encodeToString(root.raw())).put("keyset", signed(keyset, id, root)).toString()
    }

    fun list(seq: Int, channels: JSONArray, revoked: JSONArray, issuedAt: Instant, expiresAt: Instant): String {
        val body = JSONObject().put("type", "channel-list").put("format", 1).put("provider", id).put("seq", seq)
            .put("issuedAt", issuedAt.toString()).put("expiresAt", expiresAt.toString()).put("channels", channels).put("revoked", revoked)
        val bytes = body.toString().toByteArray()
        val digest = JSONObject().put("type", "channel-list-digest").put("provider", id).put("seq", seq)
            .put("issuedAt", issuedAt.toString()).put("expiresAt", expiresAt.toString()).put("sha256", sha256(bytes)).put("size", bytes.size)
        return JSONObject().put("payload", b64.encodeToString(bytes)).put("digest", signed(digest, "k-1", signing)).toString()
    }
}

/** A publisher signing its package (API-003 F-6). */
private class TestPublisher {
    private val key = ed25519()
    val accountId: String = AccountIds.of(key.raw())

    fun packageZip(channel: String, version: Int): ByteArray {
        val manifest = JSONObject(
            """
            { "format": 1, "id": "$channel", "version": $version, "publisher": "$accountId",
              "name": "鎌倉歴史散歩", "summary": "寺社と武士の歴史", "lang": "ja", "greeting": "歴史をたどりながら歩きましょう。",
              "talk": { "level": "normal", "events": { "spot": true, "revisit": true, "milestone": true, "rest": false, "start": true, "finish": true } },
              "spots": { "prefer": ["temple"], "skip": [] }, "guide": { "length": "long" }, "mood": { "tone": true, "sound": "temple" } }
            """.trimIndent(),
        )
        val files = mapOf("channel.json" to manifest.toString().toByteArray(), "prompts/guide/focus.md" to "- 由来を中心に話す\n".toByteArray())
        val payload = JSONObject().put("type", "channel-package").put("channel", channel).put("version", version).put("publisher", accountId)
            .put("files", JSONObject(files.mapValues { (_, b) -> sha256(b) })).toString().toByteArray()
        val signature = JSONObject().put("payload", b64.encodeToString(payload)).put("publisherKey", b64.encodeToString(key.raw()))
            .put("sig", b64.encodeToString(key.sign(payload))).toString().toByteArray()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in files + ("signature.json" to signature)) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
