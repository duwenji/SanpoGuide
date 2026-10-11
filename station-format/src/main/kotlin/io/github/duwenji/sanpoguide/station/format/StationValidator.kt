package io.github.duwenji.sanpoguide.station.format

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** A channel that passed every check: its settings and the text of the slots it fills. */
data class StationPackage(val manifest: StationManifest, val slots: Map<Slot, String>)

/** Why a package was turned away; the codes are the ones in API-003's error table. */
enum class RejectCode(val json: String) {
    BAD_ARCHIVE("bad_archive"),
    UNEXPECTED_FILE("unexpected_file"),
    BAD_SIGNATURE("bad_signature"),
    PUBLISHER_MISMATCH("publisher_mismatch"),
    UNSUPPORTED_FORMAT("unsupported_format"),
    BAD_MANIFEST("bad_manifest"),
    BAD_VALUE("bad_value"),
    SLOT_TOO_LONG("slot_too_long"),
    FORBIDDEN_TEXT("forbidden_text"),
    RENDER_FAILED("render_failed"),
    BAD_RESOURCE("bad_resource"),
    BAD_CUE("bad_cue"),
    UNSUPPORTED_RESOURCE("unsupported_resource"),
    RESOURCE_MISMATCH("resource_mismatch"),
}

sealed interface StationCheck {
    data class Ok(val station: StationPackage) : StationCheck
    data class Rejected(val code: RejectCode, val detail: String) : StationCheck
}

enum class StationOrigin { BUILT_IN, THIRD_PARTY }

/** What the approved channel list says about a package; the package must agree. */
data class ListedAs(val id: String, val version: Int, val publisher: String)

/**
 * The checks of API-003 ("確認の手順"), shared by the app and the review tools.
 *
 * Covers unpacking the ZIP ([checkArchive]), the files, the publisher's signature, `channel.json`
 * and the prompt slots. Not here: the package's size and SHA-256 against the list (the caller has
 * the list) and rendering the prompts ([io.github.duwenji.sanpoguide.prompt.StationPrompts]). Resources
 * and cues aren't supported yet, so a package using them is turned away as
 * [RejectCode.UNSUPPORTED_RESOURCE].
 */
object StationValidator {
    const val FORMAT = 1
    const val MAX_FILES = 100
    const val MAX_TOTAL_BYTES = 2 * 1024 * 1024
    private const val MANIFEST = "channel.json"
    private const val SIGNATURE = "signature.json"
    private val ID = Regex("[a-z0-9-]{3,40}")
    private val ACCOUNT_ID = Regex("sg1[a-z2-7]{32}")
    private val HEADING = Regex("(?m)^\\s*#")
    private val URL = Regex("https?://|www\\.", RegexOption.IGNORE_CASE)

    /**
     * Checks a channel's files. A third-party package also needs [verifier] for its publisher's
     * signature; built-in channels have none.
     */
    fun check(files: StationFiles, origin: StationOrigin, listedAs: ListedAs? = null, verifier: Ed25519Verifier? = null): StationCheck = try {
        StationCheck.Ok(validate(files, origin, listedAs, verifier))
    } catch (e: Reject) {
        StationCheck.Rejected(e.code, e.message.orEmpty())
    }

    /** Unpacks a third-party package (API-003 確認の手順 2) and checks it like [check]. */
    fun checkArchive(zip: ByteArray, listedAs: ListedAs? = null, verifier: Ed25519Verifier): StationCheck {
        val files = try {
            PackageArchive.open(zip)
        } catch (e: ArchiveException) {
            return StationCheck.Rejected(RejectCode.BAD_ARCHIVE, e.message.orEmpty())
        }
        return check(files, StationOrigin.THIRD_PARTY, listedAs, verifier)
    }

    private fun validate(files: StationFiles, origin: StationOrigin, listedAs: ListedAs?, verifier: Ed25519Verifier?): StationPackage {
        val paths = files.list()
        if (paths.size > MAX_FILES) reject(RejectCode.BAD_ARCHIVE, "${paths.size} files (max $MAX_FILES)")
        val totalBytes = paths.sumOf { files.read(it)?.size ?: 0 }
        if (totalBytes > MAX_TOTAL_BYTES) reject(RejectCode.BAD_ARCHIVE, "$totalBytes bytes (max $MAX_TOTAL_BYTES)")

        val json = parseManifest(files)
        val allowed = buildSet {
            add(MANIFEST)
            Slot.entries.forEach { add(it.path) }
            if (origin == StationOrigin.THIRD_PARTY) add(SIGNATURE)
        }
        paths.firstOrNull { it !in allowed }?.let { reject(RejectCode.UNEXPECTED_FILE, it) }
        if (origin == StationOrigin.THIRD_PARTY && SIGNATURE !in paths) reject(RejectCode.BAD_SIGNATURE, "no $SIGNATURE")
        val signed = if (origin == StationOrigin.THIRD_PARTY) {
            requireNotNull(verifier) { "a third-party package needs a signature verifier" }
            verifySignature(files, paths, verifier)
        } else {
            null
        }

        val manifest = readManifest(json, origin)
        if (signed != null) {
            if (signed.channel != manifest.id || signed.version != manifest.version) {
                reject(RejectCode.BAD_SIGNATURE, "signed for ${signed.channel}@${signed.version}, not ${manifest.id}@${manifest.version}")
            }
            // The key that signed must be the publisher the package and the list name (API-003 F-6).
            if (signed.accountId != signed.publisher || signed.accountId != manifest.publisher) {
                reject(RejectCode.PUBLISHER_MISMATCH, "signed by ${signed.accountId}, package says ${manifest.publisher}")
            }
        }
        if (listedAs != null) {
            if (manifest.id != listedAs.id || manifest.version != listedAs.version) {
                reject(RejectCode.BAD_MANIFEST, "${manifest.id}@${manifest.version} is listed as ${listedAs.id}@${listedAs.version}")
            }
            if (manifest.publisher != listedAs.publisher) reject(RejectCode.PUBLISHER_MISMATCH, "publisher ${manifest.publisher}")
        }
        // Checked by the media stages of the app; until then a channel that needs them can't be shown.
        listOf("resources", "cues").forEach { key ->
            val items = json.opt(key)
            if (items != null && items != JSONObject.NULL) {
                if (items !is JSONArray) reject(RejectCode.BAD_MANIFEST, "$key is not a list")
                if (items.length() > 0) reject(RejectCode.UNSUPPORTED_RESOURCE, "$key are not supported yet")
            }
        }
        return StationPackage(manifest, readSlots(files, paths))
    }

    /**
     * `signature.json` (API-003 F-6): the publisher's Ed25519 signature over the SHA-256 of every
     * other file. Every file must be listed and match, and nothing else may be listed.
     */
    private fun verifySignature(files: StationFiles, paths: List<String>, verifier: Ed25519Verifier): SignedFiles {
        val doc = try {
            JSONObject(decode(files.read(SIGNATURE)!!, SIGNATURE, RejectCode.BAD_SIGNATURE))
        } catch (e: JSONException) {
            reject(RejectCode.BAD_SIGNATURE, "$SIGNATURE: ${e.message}")
        }
        fun bytes(key: String): ByteArray =
            decodeBase64Url(doc.optString(key, "")) ?: reject(RejectCode.BAD_SIGNATURE, "$SIGNATURE: $key is not base64url")
        val payloadBytes = bytes("payload")
        val publicKey = bytes("publisherKey")
        if (publicKey.size != 32) reject(RejectCode.BAD_SIGNATURE, "publisherKey is not 32 bytes")
        if (!verifier.verify(publicKey, payloadBytes, bytes("sig"))) reject(RejectCode.BAD_SIGNATURE, "the signature does not verify")

        val payload = try {
            JSONObject(decode(payloadBytes, "$SIGNATURE payload", RejectCode.BAD_SIGNATURE))
        } catch (e: JSONException) {
            reject(RejectCode.BAD_SIGNATURE, "payload: ${e.message}")
        }
        if (payload.optString("type") != "channel-package") reject(RejectCode.BAD_SIGNATURE, "payload type ${payload.optString("type")}")
        val listed = payload.optJSONObject("files") ?: reject(RejectCode.BAD_SIGNATURE, "payload has no files")
        val signedPaths = listed.keys().asSequence().toSet() // keys(): Android's org.json has no keySet()
        val present = paths.filter { it != SIGNATURE }.toSet()
        (present - signedPaths).firstOrNull()?.let { reject(RejectCode.BAD_SIGNATURE, "$it is not signed") }
        (signedPaths - present).firstOrNull()?.let { reject(RejectCode.BAD_SIGNATURE, "$it is signed but missing") }
        for (path in present) {
            if (sha256(files.read(path)!!).hex() != listed.optString(path)) reject(RejectCode.BAD_SIGNATURE, "$path differs from what was signed")
        }
        val version = payload.opt("version")
        return SignedFiles(
            channel = payload.optString("channel"),
            version = if (version is Int) version else reject(RejectCode.BAD_SIGNATURE, "payload version is not an integer"),
            publisher = payload.optString("publisher"),
            accountId = AccountIds.of(publicKey),
        )
    }

    private fun parseManifest(files: StationFiles): JSONObject {
        val bytes = files.read(MANIFEST) ?: reject(RejectCode.BAD_MANIFEST, "no $MANIFEST")
        return try {
            JSONObject(decode(bytes, MANIFEST, RejectCode.BAD_MANIFEST))
        } catch (e: JSONException) {
            reject(RejectCode.BAD_MANIFEST, "$MANIFEST: ${e.message}")
        }
    }

    private fun readManifest(json: JSONObject, origin: StationOrigin): StationManifest {
        val format = json.int("format")
        if (format != FORMAT) reject(RejectCode.UNSUPPORTED_FORMAT, "format $format")
        val id = json.string("id")
        if (!ID.matches(id)) reject(RejectCode.BAD_VALUE, "id \"$id\"")
        val version = json.int("version")
        if (version < 1) reject(RejectCode.BAD_VALUE, "version $version")
        val publisher = json.optNullable("publisher")?.let { it as? String ?: reject(RejectCode.BAD_MANIFEST, "publisher is not a string") }
        when (origin) {
            StationOrigin.BUILT_IN -> if (publisher != null) reject(RejectCode.BAD_MANIFEST, "a built-in channel has no publisher")
            StationOrigin.THIRD_PARTY -> if (publisher == null || !ACCOUNT_ID.matches(publisher)) {
                reject(RejectCode.BAD_MANIFEST, "publisher \"$publisher\"")
            }
        }
        val lang = json.string("lang")
        if (lang != "ja") reject(RejectCode.BAD_VALUE, "lang \"$lang\"")

        val talk = json.obj("talk")
        val events = talk.obj("events")
        val spots = json.obj("spots")
        val prefer = spots.kinds("prefer")
        val skip = spots.kinds("skip")
        if (prefer.size > 8) reject(RejectCode.BAD_VALUE, "spots.prefer has ${prefer.size} kinds (max 8)")
        (prefer intersect skip.toSet()).firstOrNull()?.let { reject(RejectCode.BAD_VALUE, "${it.json} is both preferred and skipped") }
        val mood = json.obj("mood")

        return StationManifest(
            format = format,
            id = id,
            version = version,
            publisher = publisher,
            name = json.string("name").also { checkLength("name", it, 1, 20) },
            summary = json.string("summary").also { checkLength("summary", it, 1, 60) },
            lang = lang,
            greeting = json.string("greeting").also { checkLength("greeting", it, 1, 60) },
            talkLevel = talk.enum<TalkLevel>("level"),
            events = TalkEventKind.entries.filter { events.bool(it.json) }.toSet(),
            prefer = prefer,
            skip = skip,
            guideLength = json.obj("guide").enum<GuideLength>("length"),
            moodTone = mood.bool("tone"),
            sound = mood.enum<SoundChoice>("sound"),
        )
    }

    private fun readSlots(files: StationFiles, paths: List<String>): Map<Slot, String> {
        val slots = Slot.entries.filter { it.path in paths }.associateWith { slot ->
            val text = decode(files.read(slot.path)!!, slot.path, RejectCode.FORBIDDEN_TEXT)
            if (text.startsWith('﻿')) reject(RejectCode.FORBIDDEN_TEXT, "${slot.path}: byte order mark")
            if ('\r' in text) reject(RejectCode.FORBIDDEN_TEXT, "${slot.path}: CR line breaks")
            if ("{{" in text) reject(RejectCode.FORBIDDEN_TEXT, "${slot.path}: \"{{\"")
            URL.find(text)?.let { reject(RejectCode.FORBIDDEN_TEXT, "${slot.path}: URL \"${it.value}\"") }
            if (HEADING.containsMatchIn(text)) reject(RejectCode.FORBIDDEN_TEXT, "${slot.path}: heading")
            val trimmed = text.trim()
            if (trimmed.isEmpty()) reject(RejectCode.BAD_VALUE, "${slot.path} is empty")
            val chars = trimmed.charCount()
            if (chars > slot.maxChars) reject(RejectCode.SLOT_TOO_LONG, "${slot.path}: $chars chars (max ${slot.maxChars})")
            trimmed
        }
        val total = slots.values.sumOf { it.charCount() }
        if (total > Slot.MAX_TOTAL_CHARS) reject(RejectCode.SLOT_TOO_LONG, "slots total $total chars (max ${Slot.MAX_TOTAL_CHARS})")
        return slots
    }

    private fun decode(bytes: ByteArray, path: String, code: RejectCode): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        reject(code, "$path is not UTF-8")
    }

    private fun checkLength(field: String, value: String, min: Int, max: Int) {
        val chars = value.charCount()
        if (chars < min || chars > max) reject(RejectCode.BAD_VALUE, "$field has $chars chars ($min..$max)")
    }

    /** Characters as a reader counts them (one per code point, so emoji and rare kanji count once). */
    private fun String.charCount(): Int = codePointCount(0, length)

    private fun JSONObject.optNullable(key: String): Any? = opt(key)?.takeIf { it != JSONObject.NULL }

    private fun JSONObject.required(key: String): Any = optNullable(key) ?: reject(RejectCode.BAD_MANIFEST, "no \"$key\"")

    private fun JSONObject.string(key: String): String =
        required(key) as? String ?: reject(RejectCode.BAD_MANIFEST, "\"$key\" is not a string")

    private fun JSONObject.int(key: String): Int =
        required(key) as? Int ?: reject(RejectCode.BAD_MANIFEST, "\"$key\" is not an integer")

    private fun JSONObject.bool(key: String): Boolean =
        required(key) as? Boolean ?: reject(RejectCode.BAD_MANIFEST, "\"$key\" is not true or false")

    private fun JSONObject.obj(key: String): JSONObject =
        required(key) as? JSONObject ?: reject(RejectCode.BAD_MANIFEST, "\"$key\" is not an object")

    private inline fun <reified E> JSONObject.enum(key: String): E where E : Enum<E>, E : JsonValue {
        val value = string(key)
        return parseEnum<E>(value) ?: reject(RejectCode.BAD_VALUE, "$key \"$value\"")
    }

    private fun JSONObject.kinds(key: String): List<SpotKind> {
        val array = required(key) as? JSONArray ?: reject(RejectCode.BAD_MANIFEST, "\"$key\" is not a list")
        val kinds = (0 until array.length()).map { i ->
            val value = array.opt(i) as? String ?: reject(RejectCode.BAD_MANIFEST, "\"$key\" has a non-string item")
            parseEnum<SpotKind>(value) ?: reject(RejectCode.BAD_VALUE, "$key \"$value\"")
        }
        if (kinds.toSet().size != kinds.size) reject(RejectCode.BAD_VALUE, "$key has duplicates")
        return kinds
    }

    private class Reject(val code: RejectCode, detail: String) : Exception(detail)

    private fun reject(code: RejectCode, detail: String): Nothing = throw Reject(code, detail)
}
