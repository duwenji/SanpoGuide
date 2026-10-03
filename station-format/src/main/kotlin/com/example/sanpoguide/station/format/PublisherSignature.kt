package com.example.sanpoguide.station.format

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Checks a pure Ed25519 signature (RFC 8032). Pluggable because the platforms differ: the JVM
 * (and Android 13+) have it built in ([JcaEd25519]); the app, from Android 8, passes one backed by
 * Tink, as for API-001.
 */
fun interface Ed25519Verifier {
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean
}

/** Ed25519 from the platform's own crypto (Java 15+, Android 13+): the review tools use this. */
object JcaEd25519 : Ed25519Verifier {
    // DER prefix of an Ed25519 SubjectPublicKeyInfo; the raw 32-byte key follows it.
    private val SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

    override fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = try {
        val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(SPKI_PREFIX + publicKey))
        Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(message)
            verify(signature)
        }
    } catch (e: java.security.GeneralSecurityException) {
        false
    }
}

/** A publisher's account id: `sg1` + the SHA-256 of the public key in base32, first 32 characters (API-001). */
object AccountIds {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

    fun of(publicKey: ByteArray): String {
        require(publicKey.size == 32) { "an Ed25519 public key is 32 bytes" }
        return "sg1" + base32(sha256(publicKey)).take(32)
    }

    private fun base32(bytes: ByteArray): String = buildString {
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) append(ALPHABET[(buffer shl (5 - bits)) and 31])
    }
}

internal fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

private val BASE64URL = Regex("[A-Za-z0-9_-]*")

/** Strict base64url (no padding, nothing else), or null. */
internal fun decodeBase64Url(text: String): ByteArray? =
    if (BASE64URL.matches(text)) runCatching { Base64.getUrlDecoder().decode(text) }.getOrNull() else null

/** What a verified `signature.json` vouches for (API-003 F-6). */
internal data class SignedFiles(val channel: String, val version: Int, val publisher: String, val accountId: String)
