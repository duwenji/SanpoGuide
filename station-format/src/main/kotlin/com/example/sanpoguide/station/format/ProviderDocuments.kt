package com.example.sanpoguide.station.format

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * The app's checks of a channel provider's documents (API-002): the discovery document and its
 * keyset, the channel list, and test tickets. The same rules as the channel management system's
 * conformance checks (sanpo-channel-console `packages/protocol`), whose code signs the test
 * vectors these are tested against.
 */

/** API-002's error codes (エラー表) that a document check can give. */
enum class ProviderError(val code: String) {
    PROVIDER_MISMATCH("provider_mismatch"),
    UNSUPPORTED_VERSION("unsupported_version"),
    BAD_SIGNATURE("bad_signature"),
    UNKNOWN_KEY("unknown_key"),
    KEY_NOT_VALID("key_not_valid"),
    ROLLBACK("rollback"),
    EXPIRED("expired"),
    TOO_LARGE("too_large"),
    TICKET_INVALID("ticket_invalid"),

    /** Not JSON, or a field missing or of the wrong type. */
    BAD_DOCUMENT("bad_document"),
}

class ProviderException(val error: ProviderError, detail: String) : Exception("${error.code}: $detail")

/** A provider's id: `sc1` + the SHA-256 of its root public key in base32, first 32 characters (API-002 P-2). */
object ProviderIds {
    private val FORM = Regex("sc1[a-z2-7]{32}")

    fun of(rootKey: ByteArray): String {
        require(rootKey.size == 32) { "an Ed25519 public key is 32 bytes" }
        return "sc1" + base32(sha256(rootKey)).take(32)
    }

    fun isWellFormed(id: String): Boolean = FORM.matches(id)
}

class SigningKey(val keyId: String, val publicKey: ByteArray, val notBefore: Instant, val notAfter: Instant) {
    fun validAt(at: Instant): Boolean = !at.isBefore(notBefore) && !at.isAfter(notAfter)
}

class Keyset(val provider: String, val seq: Int, val keys: List<SigningKey>, val revokedKeys: Set<String>)

/** A provider whose discovery document and keyset check out against the provider id the user registered. */
class ProviderInfo(
    val id: String,
    val name: String,
    /** The list's path or URL, as the discovery document gives it. */
    val list: String,
    val keyset: Keyset,
    val reviewPolicyUrl: String?,
    val termsUrl: String?,
    val contact: String?,
)

class FileRef(val url: String, val sha256: String)

class PackageRef(val url: String, val sha256: String, val size: Int, val format: Int)

class PublisherChange(val from: String, val at: Instant, val reason: String)

/** One entry of `channels[]`. */
class ListedChannel(
    val id: String,
    val version: Int,
    val publisher: String,
    val publisherName: String,
    val publisherChange: PublisherChange?,
    val name: String,
    val summary: String,
    val description: String?,
    val lang: List<String>,
    val tags: List<String>,
    val regions: List<String>,
    val icon: FileRef,
    val pkg: PackageRef,
    val minAppVersion: Int,
    val approvedAt: Instant,
)

class RevokedChannel(val id: String, val versions: List<Int>?, val reason: String, val revokedAt: Instant) {
    fun covers(version: Int): Boolean = versions == null || version in versions
}

class ChannelList(
    val provider: String,
    val seq: Int,
    val issuedAt: Instant,
    val expiresAt: Instant,
    /** The SHA-256 of the list's body: a list with the same [seq] and another hash is a rollback. */
    val sha256: String,
    val channels: List<ListedChannel>,
    val revoked: List<RevokedChannel>,
)

class TestTicket(val provider: String, val channel: String, val publisher: String, val pkg: PackageRef, val issuedAt: Instant, val expiresAt: Instant)

class ProviderDocuments(private val ed25519: Ed25519Verifier) {
    companion object {
        const val MAX_LIST_BYTES = 1024 * 1024
        const val MAX_PACKAGE_BYTES = 2 * 1024 * 1024
        const val MAX_ICON_BYTES = 100 * 1024
        val LIST_LIFETIME: Duration = Duration.ofDays(14)
        val TICKET_LIFETIME: Duration = Duration.ofDays(7)
        val KEY_LIFETIME: Duration = Duration.ofDays(366)

        /** A list issued this far ahead of the device's clock suggests the clock is wrong (API-002 エラー表の注). */
        val CLOCK_SKEW: Duration = Duration.ofDays(1)
    }

    /**
     * `/.well-known/sanpo-channels` (V-01, V-02): the root key must give [expectedProvider], the id
     * the user registered (never taken from the server), and the keyset must be signed by it.
     */
    fun discovery(json: String, expectedProvider: String, minKeysetSeq: Int? = null): ProviderInfo = parsing("discovery") {
        val doc = JSONObject(json)
        val versions = doc.optJSONArray("versions")
        if (versions == null || (0 until versions.length()).none { versions.opt(it) == "v1" }) {
            fail(ProviderError.UNSUPPORTED_VERSION, "versions has no v1")
        }
        val rootKey = bytes(doc.getString("rootKey"), "rootKey")
        if (rootKey.size != 32) fail(ProviderError.BAD_DOCUMENT, "rootKey is not 32 bytes")
        val provider = ProviderIds.of(rootKey)
        if (provider != doc.getString("provider")) fail(ProviderError.PROVIDER_MISMATCH, "rootKey gives $provider, the document says ${doc.getString("provider")}")
        if (provider != expectedProvider) fail(ProviderError.PROVIDER_MISMATCH, "rootKey gives $provider, registered as $expectedProvider")

        val signed = doc.getJSONObject("keyset")
        if (signed.getString("keyId") != provider) fail(ProviderError.UNKNOWN_KEY, "the keyset is not signed by the root key")
        val keyset = open(signed, rootKey, "keyset", "keyset").json
        if (keyset.getString("provider") != provider) fail(ProviderError.PROVIDER_MISMATCH, "the keyset is for ${keyset.getString("provider")}")
        val seq = keyset.getInt("seq")
        if (minKeysetSeq != null && seq < minKeysetSeq) fail(ProviderError.ROLLBACK, "keyset seq $seq < $minKeysetSeq")
        val keys = keyset.getJSONArray("keys").objects().map { k ->
            val publicKey = bytes(k.getString("publicKey"), "keys[].publicKey")
            if (publicKey.size != 32) fail(ProviderError.BAD_DOCUMENT, "a signing key is not 32 bytes")
            val key = SigningKey(k.getString("keyId"), publicKey, instant(k.getString("notBefore")), instant(k.getString("notAfter")))
            if (!key.notAfter.isAfter(key.notBefore) || Duration.between(key.notBefore, key.notAfter) > KEY_LIFETIME) {
                fail(ProviderError.BAD_DOCUMENT, "${key.keyId} is valid for more than a year")
            }
            key
        }
        ProviderInfo(
            id = provider,
            name = doc.getString("name"),
            list = doc.getString("list"),
            keyset = Keyset(provider, seq, keys, keyset.getJSONArray("revokedKeys").strings().toSet()),
            reviewPolicyUrl = doc.optStringOrNull("reviewPolicyUrl"),
            termsUrl = doc.optStringOrNull("termsUrl"),
            contact = doc.optStringOrNull("contact"),
        )
    }

    /**
     * `/v1/channels.json` (V-03, V-04, P-8): the digest's signature by a valid signing key, the body
     * against the digest, the dates, and no going back to an older list ([previousSeq] and
     * [previousSha256] are the last list the app accepted).
     */
    fun channelList(json: String, keyset: Keyset, now: Instant, previousSeq: Int? = null, previousSha256: String? = null): ChannelList = parsing("list") {
        if (json.length > MAX_LIST_BYTES * 2) fail(ProviderError.TOO_LARGE, "the list is over ${MAX_LIST_BYTES} bytes")
        val doc = JSONObject(json)
        val (digest, key) = openWithSigningKey(doc.getJSONObject("digest"), keyset, "channel-list-digest", "digest")
        val issuedAt = instant(digest.getString("issuedAt"))
        if (!key.validAt(issuedAt)) fail(ProviderError.KEY_NOT_VALID, "${key.keyId} was not valid at $issuedAt")

        val body = bytes(doc.getString("payload"), "payload")
        if (body.size > MAX_LIST_BYTES) fail(ProviderError.TOO_LARGE, "the list is ${body.size} bytes")
        val sha256 = sha256(body).hex()
        if (body.size != digest.getInt("size") || sha256 != digest.getString("sha256")) fail(ProviderError.BAD_SIGNATURE, "the body does not match the digest")
        val list = JSONObject(String(body, Charsets.UTF_8))
        if (list.optString("type") != "channel-list") fail(ProviderError.BAD_SIGNATURE, "the body is not a channel list")
        if (list.optInt("format") != 1) fail(ProviderError.UNSUPPORTED_VERSION, "list format ${list.opt("format")}")
        for (field in listOf("provider", "seq", "issuedAt", "expiresAt")) {
            if (list.opt(field) != digest.opt(field)) fail(ProviderError.BAD_SIGNATURE, "the body's $field differs from the digest")
        }
        if (list.getString("provider") != keyset.provider) fail(ProviderError.PROVIDER_MISMATCH, "the list is for ${list.getString("provider")}")

        val seq = list.getInt("seq")
        val expiresAt = instant(list.getString("expiresAt"))
        if (Duration.between(issuedAt, expiresAt) > LIST_LIFETIME) fail(ProviderError.EXPIRED, "expiresAt is more than 14 days after issuedAt")
        if (!now.isBefore(expiresAt)) fail(ProviderError.EXPIRED, "expired at $expiresAt")
        if (previousSeq != null && (seq < previousSeq || (seq == previousSeq && previousSha256 != null && sha256 != previousSha256))) {
            fail(ProviderError.ROLLBACK, "seq $seq after $previousSeq")
        }
        ChannelList(
            provider = keyset.provider,
            seq = seq,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            sha256 = sha256,
            channels = list.getJSONArray("channels").objects().map(::listedChannel),
            revoked = list.getJSONArray("revoked").objects().map { r ->
                RevokedChannel(r.getString("id"), r.optJSONArray("versions")?.ints(), r.getString("reason"), instant(r.getString("revokedAt")))
            },
        )
    }

    /** Whether a list rejected as expired was issued ahead of the device's clock (then the clock is suspect, not the list). */
    fun issuedInFuture(json: String, now: Instant): Boolean = runCatching {
        val digest = JSONObject(String(bytes(JSONObject(json).getJSONObject("digest").getString("payload"), "digest"), Charsets.UTF_8))
        instant(digest.getString("issuedAt")).isAfter(now.plus(CLOCK_SKEW))
    }.getOrDefault(false)

    /**
     * A test ticket read from a QR code (API-002 試用チケット): signed by a valid signing key of the
     * provider, valid now, for at most 7 days. Whether the app is in developer mode is the app's check.
     */
    fun testTicket(json: String, keyset: Keyset, now: Instant): TestTicket = parsing("ticket") {
        try {
            val (ticket, key) = openWithSigningKey(JSONObject(json), keyset, "test-ticket", "ticket")
            val issuedAt = instant(ticket.getString("issuedAt"))
            val expiresAt = instant(ticket.getString("expiresAt"))
            if (!key.validAt(issuedAt)) fail(ProviderError.TICKET_INVALID, "${key.keyId} was not valid at $issuedAt")
            if (ticket.getString("provider") != keyset.provider) fail(ProviderError.TICKET_INVALID, "the ticket is for ${ticket.getString("provider")}")
            if (Duration.between(issuedAt, expiresAt) > TICKET_LIFETIME) fail(ProviderError.TICKET_INVALID, "valid for more than 7 days")
            if (!now.isBefore(expiresAt)) fail(ProviderError.TICKET_INVALID, "expired at $expiresAt")
            TestTicket(ticket.getString("provider"), ticket.getString("channel"), ticket.getString("publisher"), packageRef(ticket.getJSONObject("package")), issuedAt, expiresAt)
        } catch (e: ProviderException) {
            if (e.error == ProviderError.TICKET_INVALID) throw e
            throw ProviderException(ProviderError.TICKET_INVALID, e.message ?: "")
        }
    }

    // ---- helpers ----

    private class Opened(val json: JSONObject)

    /** Checks a signature and the payload's `type`, which keeps one kind of document from passing for another (P-3). */
    private fun open(signed: JSONObject, publicKey: ByteArray, type: String, what: String): Opened {
        val payload = bytes(signed.getString("payload"), "$what.payload")
        if (!ed25519.verify(publicKey, payload, bytes(signed.getString("sig"), "$what.sig"))) fail(ProviderError.BAD_SIGNATURE, "$what: the signature does not verify")
        val json = JSONObject(String(payload, Charsets.UTF_8))
        if (json.optString("type") != type) fail(ProviderError.BAD_SIGNATURE, "$what: type is ${json.opt("type")}, not $type")
        return Opened(json)
    }

    private fun openWithSigningKey(signed: JSONObject, keyset: Keyset, type: String, what: String): Pair<JSONObject, SigningKey> {
        val keyId = signed.getString("keyId")
        val key = keyset.keys.firstOrNull { it.keyId == keyId } ?: fail(ProviderError.UNKNOWN_KEY, "$keyId is not in the keyset")
        if (keyId in keyset.revokedKeys) fail(ProviderError.KEY_NOT_VALID, "$keyId is revoked")
        return open(signed, key.publicKey, type, what).json to key
    }

    private fun listedChannel(c: JSONObject) = ListedChannel(
        id = c.getString("id"),
        version = c.getInt("version"),
        publisher = c.getString("publisher"),
        publisherName = c.getString("publisherName"),
        publisherChange = c.optJSONObject("publisherChange")?.let { PublisherChange(it.getString("from"), instant(it.getString("at")), it.getString("reason")) },
        name = c.getString("name"),
        summary = c.getString("summary"),
        description = c.optStringOrNull("description"),
        lang = c.getJSONArray("lang").strings(),
        tags = c.optJSONArray("tags")?.strings().orEmpty(),
        regions = c.optJSONArray("regions")?.strings().orEmpty(),
        icon = c.getJSONObject("icon").let { FileRef(it.getString("url"), it.getString("sha256")) },
        pkg = packageRef(c.getJSONObject("package")),
        minAppVersion = c.getInt("minAppVersion"),
        approvedAt = instant(c.getString("approvedAt")),
    )

    private fun packageRef(p: JSONObject) = PackageRef(p.getString("url"), p.getString("sha256"), p.getInt("size"), p.getInt("format"))

    private fun bytes(text: String, what: String): ByteArray = decodeBase64Url(text) ?: fail(ProviderError.BAD_DOCUMENT, "$what is not base64url")

    private fun instant(text: String): Instant = try {
        Instant.parse(text)
    } catch (e: DateTimeParseException) {
        fail(ProviderError.BAD_DOCUMENT, "$text is not a date")
    }

    /** Turns a malformed document (a missing field, wrong type, not JSON) into [ProviderError.BAD_DOCUMENT]. */
    private inline fun <T> parsing(what: String, block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        fail(if (what == "ticket") ProviderError.TICKET_INVALID else ProviderError.BAD_DOCUMENT, "$what: ${e.message}")
    }

    private fun fail(error: ProviderError, detail: String): Nothing = throw ProviderException(error, detail)
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
