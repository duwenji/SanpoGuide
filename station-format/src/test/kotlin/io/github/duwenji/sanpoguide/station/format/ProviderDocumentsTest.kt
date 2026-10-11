package io.github.duwenji.sanpoguide.station.format

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.EdECPrivateKeySpec
import java.security.spec.NamedParameterSpec
import java.time.Instant
import java.util.Base64

/**
 * API-002 as the app checks it, against documents the channel management system's own code signed
 * (`provider-vectors.json`, from sanpo-channel-console `conformance/src/app-vectors.ts`). The
 * rejections re-sign altered documents with the same fixed, public seeds (1 = root, 2 = signing).
 */
class ProviderDocumentsTest {
    private val vectors = JSONObject(javaClass.getResource("/provider-vectors.json")!!.readText())
    private val provider = vectors.getString("provider")
    private val now = Instant.parse(vectors.getString("now"))
    private val docs = ProviderDocuments(JcaEd25519)
    private val discovery = vectors.getJSONObject("discovery").toString()
    private val keyset = docs.discovery(discovery, provider).keyset

    private fun error(block: () -> Unit): ProviderError {
        try {
            block()
        } catch (e: ProviderException) {
            return e.error
        }
        fail("expected a ProviderException")
        throw AssertionError()
    }

    // ---- signing with the vectors' seeds ----

    private fun sign(seed: Int, message: ByteArray): ByteArray {
        val key = KeyFactory.getInstance("Ed25519").generatePrivate(EdECPrivateKeySpec(NamedParameterSpec.ED25519, ByteArray(32) { seed.toByte() }))
        return Signature.getInstance("Ed25519").run {
            initSign(key)
            update(message)
            sign()
        }
    }

    private val b64 = Base64.getUrlEncoder().withoutPadding()

    private fun signed(content: JSONObject, keyId: String, seed: Int): JSONObject {
        val bytes = content.toString().toByteArray()
        return JSONObject().put("payload", b64.encodeToString(bytes)).put("keyId", keyId).put("sig", b64.encodeToString(sign(seed, bytes)))
    }

    private fun payloadOf(signed: JSONObject) = JSONObject(String(Base64.getUrlDecoder().decode(signed.getString("payload"))))

    /** The vectors' list with its body changed by [edit], re-signed so only the change is wrong. */
    private fun listWith(keyId: String = "k-2026-10", editDigest: (JSONObject) -> Unit = {}, edit: (JSONObject) -> Unit = {}): String {
        val list = vectors.getJSONObject("list")
        val body = JSONObject(String(Base64.getUrlDecoder().decode(list.getString("payload"))))
        edit(body)
        val bytes = body.toString().toByteArray()
        val digest = payloadOf(list.getJSONObject("digest"))
            .put("issuedAt", body.getString("issuedAt")).put("expiresAt", body.getString("expiresAt")).put("seq", body.getInt("seq"))
            .put("sha256", sha256(bytes).hex()).put("size", bytes.size)
        editDigest(digest)
        return JSONObject().put("payload", b64.encodeToString(bytes)).put("digest", signed(digest, keyId, 2)).toString()
    }

    private fun ticketWith(edit: (JSONObject) -> Unit): String = signed(payloadOf(vectors.getJSONObject("ticket")).also(edit), "k-2026-10", 2).toString()

    // ---- discovery and keyset ----

    @Test
    fun `provider ids match the channel management system's`() {
        assertEquals(provider, ProviderIds.of(Base64.getUrlDecoder().decode(vectors.getJSONObject("discovery").getString("rootKey"))))
        assertTrue(ProviderIds.isWellFormed(provider))
        assertFalse(ProviderIds.isWellFormed("sg1" + provider.drop(3)))
    }

    @Test
    fun `the discovery document and its keyset check out against the registered id`() {
        val info = docs.discovery(discovery, provider)
        assertEquals("検証用の提供元", info.name)
        assertEquals("/v1/channels.json", info.list)
        assertEquals("https://example.com/policy", info.reviewPolicyUrl)
        assertEquals(2, info.keyset.seq)
        assertEquals(listOf("k-2026-10"), info.keyset.keys.map { it.keyId })
        assertEquals(setOf("k-2026-04"), info.keyset.revokedKeys)
    }

    @Test
    fun `a discovery document is refused for another id, an old keyset, a bad signature or an unknown version`() {
        assertEquals(ProviderError.PROVIDER_MISMATCH, error { docs.discovery(discovery, "sc1" + "a".repeat(32)) })
        assertEquals(ProviderError.ROLLBACK, error { docs.discovery(discovery, provider, minKeysetSeq = 3) })
        val tampered = JSONObject(discovery).apply {
            getJSONObject("keyset").put("sig", b64.encodeToString(ByteArray(64)))
        }
        assertEquals(ProviderError.BAD_SIGNATURE, error { docs.discovery(tampered.toString(), provider) })
        val signedBySigningKey = JSONObject(discovery).put("keyset", signed(payloadOf(JSONObject(discovery).getJSONObject("keyset")), "k-2026-10", 2))
        assertEquals(ProviderError.UNKNOWN_KEY, error { docs.discovery(signedBySigningKey.toString(), provider) })
        assertEquals(ProviderError.UNSUPPORTED_VERSION, error { docs.discovery(JSONObject(discovery).put("versions", listOf("v2")).toString(), provider) })
        assertEquals(ProviderError.BAD_DOCUMENT, error { docs.discovery("""{"versions":["v1"]}""", provider) })
        assertEquals(ProviderError.BAD_DOCUMENT, error { docs.discovery("not json", provider) })
    }

    // ---- the channel list ----

    @Test
    fun `the list checks out and reads every field`() {
        val list = docs.channelList(vectors.getJSONObject("list").toString(), keyset, now)
        assertEquals(7, list.seq)
        assertEquals(listOf("kamakura-history", "tokyo-nature"), list.channels.map { it.id })
        val kamakura = list.channels[0]
        assertEquals(3, kamakura.version)
        assertEquals("sg1" + "b".repeat(32), kamakura.publisher)
        assertEquals("sg1" + "a".repeat(32), kamakura.publisherChange?.from)
        assertEquals("配信元の鍵の紛失", kamakura.publisherChange?.reason)
        assertEquals(listOf("history"), kamakura.tags)
        assertEquals(listOf("xn7"), kamakura.regions)
        assertEquals(1, kamakura.pkg.format)
        assertNull(list.channels[1].publisherChange)
        assertEquals(99, list.channels[1].minAppVersion)
        val revoked = list.revoked.single()
        assertTrue(revoked.covers(2))
        assertFalse(revoked.covers(3))
    }

    @Test
    fun `a list is refused when it is expired, rolled back, altered or signed by a key that can't sign`() {
        val json = vectors.getJSONObject("list").toString()
        assertEquals(ProviderError.EXPIRED, error { docs.channelList(json, keyset, Instant.parse("2026-10-17T03:00:00Z")) })
        assertEquals(ProviderError.ROLLBACK, error { docs.channelList(json, keyset, now, previousSeq = 8) })
        assertEquals(ProviderError.ROLLBACK, error { docs.channelList(json, keyset, now, previousSeq = 7, previousSha256 = "0".repeat(64)) })
        val same = docs.channelList(json, keyset, now)
        docs.channelList(json, keyset, now, previousSeq = 7, previousSha256 = same.sha256)

        // The body changed after signing.
        val altered = JSONObject(json).put("payload", b64.encodeToString(payloadOf(JSONObject(json)).put("seq", 7).toString().toByteArray() + ' '.code.toByte()))
        assertEquals(ProviderError.BAD_SIGNATURE, error { docs.channelList(altered.toString(), keyset, now) })
        // Signed, but differs from the digest, lives too long, or is another kind of document.
        assertEquals(ProviderError.BAD_SIGNATURE, error { docs.channelList(listWith(editDigest = { it.put("seq", 6) }), keyset, now) })
        assertEquals(ProviderError.EXPIRED, error { docs.channelList(listWith { it.put("expiresAt", "2026-10-18T03:00:00.000Z") }, keyset, now) })
        assertEquals(ProviderError.BAD_SIGNATURE, error { docs.channelList(listWith(editDigest = { it.put("type", "test-ticket") }), keyset, now) })
        assertEquals(ProviderError.UNSUPPORTED_VERSION, error { docs.channelList(listWith { it.put("format", 2) }, keyset, now) })
        // The key: unknown, revoked, or not yet valid when the list was issued.
        assertEquals(ProviderError.UNKNOWN_KEY, error { docs.channelList(listWith(keyId = "k-other"), keyset, now) })
        val revoked = Keyset(keyset.provider, keyset.seq, keyset.keys, setOf("k-2026-10"))
        assertEquals(ProviderError.KEY_NOT_VALID, error { docs.channelList(json, revoked, now) })
        assertEquals(ProviderError.KEY_NOT_VALID, error { docs.channelList(listWith { it.put("issuedAt", "2026-09-30T00:00:00.000Z") }, keyset, now) })
    }

    @Test
    fun `a list issued ahead of the device's clock points at the clock, not the list`() {
        val json = vectors.getJSONObject("list").toString()
        assertTrue(docs.issuedInFuture(json, Instant.parse("2026-10-01T00:00:00Z")))
        assertFalse(docs.issuedInFuture(json, now))
    }

    // ---- test tickets ----

    @Test
    fun `a test ticket checks out for 7 days at most`() {
        val ticket = docs.testTicket(vectors.getJSONObject("ticket").toString(), keyset, now)
        assertEquals("kamakura-history", ticket.channel)
        assertEquals(18342, ticket.pkg.size)
        val json = vectors.getJSONObject("ticket").toString()
        assertEquals(ProviderError.TICKET_INVALID, error { docs.testTicket(json, keyset, Instant.parse("2026-10-10T03:00:00Z")) })
        assertEquals(ProviderError.TICKET_INVALID, error { docs.testTicket(ticketWith { it.put("expiresAt", "2026-10-11T03:00:00.000Z") }, keyset, now) })
        assertEquals(ProviderError.TICKET_INVALID, error { docs.testTicket(ticketWith { it.put("provider", "sc1" + "a".repeat(32)) }, keyset, now) })
        assertEquals(ProviderError.TICKET_INVALID, error { docs.testTicket(ticketWith { it.put("type", "keyset") }, keyset, now) })
        assertEquals(ProviderError.TICKET_INVALID, error { docs.testTicket("{\"payload\":\"x\"}", keyset, now) })
    }
}
