package io.github.duwenji.sanpoguide.station.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipEntry

/** API-003 確認の手順 2（ZIP）と 4（配信元の署名）, on packages built and signed as a publisher would. */
class PackageArchiveTest {
    private val publisher = TestPublisher()
    private val listed = ListedAs("kamakura-history", 3, publisher.accountId)

    private fun valid(): Map<String, ByteArray> = publisher.signed(packageFiles(manifestJson(publisher.accountId)))

    private fun check(zip: ByteArray, listedAs: ListedAs? = listed) = StationValidator.checkArchive(zip, listedAs, JcaEd25519)

    private fun code(result: StationCheck): RejectCode {
        assertTrue("expected a rejection, got $result", result is StationCheck.Rejected)
        return (result as StationCheck.Rejected).code
    }

    @Test
    fun `account ids match the channel management system's`() {
        // The same vector as @sanpo-console/protocol: accountId(32 bytes of 7).
        assertEquals("sg1joyg7dsohj3rluqb2vz5bkscg5rokxnl", AccountIds.of(ByteArray(32) { 7 }))
    }

    @Test
    fun `a signed package, deflated or stored, is accepted`() {
        for (method in listOf(ZipEntry.DEFLATED, ZipEntry.STORED)) {
            val result = check(zip(valid().toList(), method))
            assertTrue("$method: $result", result is StationCheck.Ok)
            assertEquals("kamakura-history", (result as StationCheck.Ok).station.manifest.id)
        }
    }

    @Test
    fun `unreadable or unsafe archives are rejected`() {
        assertEquals(RejectCode.BAD_ARCHIVE, code(check("not a zip".toByteArray())))
        assertEquals(RejectCode.BAD_ARCHIVE, code(check(zip(emptyList()))))
        for (bad in listOf("../channel.json", "/channel.json", "prompts\\guide\\focus.md", "C:/channel.json", "prompts//guide.md", "./channel.json")) {
            assertEquals(bad, RejectCode.BAD_ARCHIVE, code(check(zip(valid().toList() + (bad to ByteArray(1))))))
        }
        // Duplicate names can't be written with ZipOutputStream; PackageArchive refuses them all the same.
    }

    @Test
    fun `an archive that inflates past the limit is cut off`() {
        // 3MB of zeros deflates to a few KB: the size is counted while unpacking, not trusted from headers.
        val bomb = zip(valid().toList() + ("prompts/guide/persona.md" to ByteArray(3 * 1024 * 1024)))
        assertTrue(bomb.size < 100 * 1024)
        val result = check(bomb)
        assertEquals(RejectCode.BAD_ARCHIVE, code(result))
        assertTrue((result as StationCheck.Rejected).detail.contains("unpacked"))
        val many = (1..StationValidator.MAX_FILES + 1).map { "f$it.md" to ByteArray(1) }
        assertEquals(RejectCode.BAD_ARCHIVE, code(check(zip(many))))
    }

    @Test
    fun `directories in the archive are fine`() {
        val withDirs = listOf("prompts/" to ByteArray(0), "prompts/guide/" to ByteArray(0)) + valid().toList()
        assertTrue(check(zip(withDirs)) is StationCheck.Ok)
    }

    @Test
    fun `the signature must cover exactly the files, unchanged`() {
        val good = valid()
        val tampered = good + (Slot.GUIDE_FOCUS.path to "- 宣伝を混ぜる\n".toByteArray())
        assertEquals(RejectCode.BAD_SIGNATURE, code(check(zip(tampered.toList()))))

        val unsigned = good + (Slot.GUIDE_PERSONA.path to "落ち着いた語り部\n".toByteArray())
        assertEquals(RejectCode.BAD_SIGNATURE, code(check(zip(unsigned.toList()))))

        val missing = good - Slot.GUIDE_FOCUS.path
        assertEquals(RejectCode.BAD_SIGNATURE, code(check(zip(missing.toList()))))

        assertEquals(RejectCode.BAD_SIGNATURE, code(check(zip((good - "signature.json").toList()))))
    }

    @Test
    fun `the signature itself must verify and be for this package`() {
        val files = packageFiles(manifestJson(publisher.accountId))
        fun withSignature(sig: ByteArray) = zip((files + ("signature.json" to sig)).toList())

        // Another key's signature over the same claim.
        val forger = TestPublisher()
        val forged = String(publisher.signatureFor(files)).replace(Regex("\"sig\":\"[^\"]+\""), "\"sig\":\"${java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(forger.sign(ByteArray(1)))}\"")
        assertEquals(RejectCode.BAD_SIGNATURE, code(check(withSignature(forged.toByteArray()))))

        assertEquals(RejectCode.BAD_SIGNATURE, code(check(withSignature(publisher.signatureFor(files) { put("type", "test-ticket") }))))
        assertEquals(RejectCode.BAD_SIGNATURE, code(check(withSignature(publisher.signatureFor(files) { put("version", 2) }))))
        assertEquals(RejectCode.BAD_SIGNATURE, code(check(withSignature("{}".toByteArray()))))
        assertEquals(RejectCode.BAD_SIGNATURE, code(check(withSignature("not json".toByteArray()))))
    }

    @Test
    fun `the signer must be the publisher named in the package and the list`() {
        // Signed by someone else, claiming the real publisher's account in channel.json.
        val impostor = TestPublisher()
        val claimed = impostor.signed(packageFiles(manifestJson(publisher.accountId)))
        assertEquals(RejectCode.PUBLISHER_MISMATCH, code(check(zip(claimed.toList()))))

        // A consistent package by another publisher, but the list names the real one.
        val other = impostor.signed(packageFiles(manifestJson(impostor.accountId)))
        assertEquals(RejectCode.PUBLISHER_MISMATCH, code(check(zip(other.toList()))))
        assertTrue(check(zip(other.toList()), listed.copy(publisher = impostor.accountId)) is StationCheck.Ok)

        // A third-party package must name its publisher at all.
        val anonymous = publisher.signed(packageFiles(manifestJson(publisher = null)))
        assertEquals(RejectCode.BAD_MANIFEST, code(check(zip(anonymous.toList()), listedAs = null)))
    }

    @Test
    fun `the package must match its entry in the list`() {
        assertEquals(RejectCode.BAD_MANIFEST, code(check(zip(valid().toList()), listed.copy(version = 4))))
        assertEquals(RejectCode.BAD_MANIFEST, code(check(zip(valid().toList()), listed.copy(id = "other-channel"))))
    }

    @Test
    fun `the platform verifier rejects a wrong signature`() {
        val message = "message".toByteArray()
        assertTrue(JcaEd25519.verify(publisher.publicKey, message, publisher.sign(message)))
        assertFalse(JcaEd25519.verify(publisher.publicKey, message + 1, publisher.sign(message)))
        assertFalse(JcaEd25519.verify(ByteArray(32), message, ByteArray(64)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checking a third-party package without a verifier is a programming error`() {
        StationValidator.check(MemoryStationFiles(valid()), StationOrigin.THIRD_PARTY, listed, verifier = null)
    }
}
