package com.example.sanpoguide.station.remote

import com.example.sanpoguide.station.format.Ed25519Verifier
import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException

/** Pure Ed25519 (RFC 8032) from Tink, which works from Android 8; the platform has it only from 13. */
object TinkEd25519 : Ed25519Verifier {
    override fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = try {
        Ed25519Verify(publicKey).verify(signature, message)
        true
    } catch (e: GeneralSecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}
