package com.example.sanpoguide.station.format

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds third-party packages the way a publisher does (API-003 F-6), for tests. */
internal class TestPublisher(private val keys: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()) {
    /** The raw 32-byte key: the last 32 bytes of its SubjectPublicKeyInfo. */
    val publicKey: ByteArray = keys.public.encoded.takeLast(32).toByteArray()
    val accountId: String = AccountIds.of(publicKey)

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun sign(message: ByteArray): ByteArray = Signature.getInstance("Ed25519").run {
        initSign(keys.private)
        update(message)
        sign()
    }

    /** `signature.json` over [files], with the payload fields overridable to build bad ones. */
    fun signatureFor(files: Map<String, ByteArray>, change: JSONObject.() -> Unit = {}): ByteArray {
        val manifest = JSONObject(String(files.getValue("channel.json")))
        val payload = JSONObject()
            .put("type", "channel-package")
            .put("channel", manifest.optString("id"))
            .put("version", manifest.optInt("version"))
            .put("publisher", accountId)
            .put("files", JSONObject(files.mapValues { (_, bytes) -> sha256(bytes).hex() }))
            .apply(change)
            .toString()
            .toByteArray()
        return JSONObject()
            .put("payload", b64(payload))
            .put("publisherKey", b64(publicKey))
            .put("sig", b64(sign(payload)))
            .toString()
            .toByteArray()
    }

    /** The files of a valid package by this publisher: the given ones plus their signature. */
    fun signed(files: Map<String, ByteArray>): Map<String, ByteArray> = files + ("signature.json" to signatureFor(files))
}

internal fun manifestJson(publisher: String?, change: JSONObject.() -> Unit = {}): JSONObject = JSONObject(
    """
    {
      "format": 1, "id": "kamakura-history", "version": 3,
      "name": "鎌倉歴史散歩", "summary": "鎌倉の寺社と武士の歴史を、語り部の口調で", "lang": "ja",
      "greeting": "ここからは、鎌倉の歴史をたどりながら歩きましょう。",
      "talk": { "level": "normal",
                "events": { "spot": true, "revisit": true, "milestone": true, "rest": false, "start": true, "finish": true } },
      "spots": { "prefer": ["temple", "shrine", "historic"], "skip": ["artwork"] },
      "guide": { "length": "long" },
      "mood": { "tone": true, "sound": "temple" }
    }
    """.trimIndent(),
).apply { if (publisher != null) put("publisher", publisher) }.apply(change)

internal fun packageFiles(manifest: JSONObject): Map<String, ByteArray> = mapOf(
    "channel.json" to manifest.toString().toByteArray(),
    Slot.GUIDE_FOCUS.path to "- 由来と、関わった人物を中心に話す\n".toByteArray(),
)

/** A ZIP of [files], in the given order, with deflate or store. */
internal fun zip(files: List<Pair<String, ByteArray>>, method: Int = ZipEntry.DEFLATED): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in files) {
            val entry = ZipEntry(name)
            if (method == ZipEntry.STORED) {
                entry.method = ZipEntry.STORED
                entry.size = bytes.size.toLong()
                entry.crc = java.util.zip.CRC32().apply { update(bytes) }.value
            }
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}
